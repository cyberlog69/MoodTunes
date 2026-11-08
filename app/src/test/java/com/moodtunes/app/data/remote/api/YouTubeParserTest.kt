package com.moodtunes.app.data.remote.api

import com.moodtunes.app.domain.model.YouTubeItem
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YouTubeParserTest {

    private fun track(
        videoId: String,
        title: String,
        subtitle: String,
        duration: String = "3:42"
    ): String = """
        {
          "musicResponsiveListItemRenderer": {
            "flexColumns": [
              {
                "musicResponsiveListItemFlexColumnRenderer": {
                  "text": { "runs": [{ "text": "$title" }] }
                }
              },
              {
                "musicResponsiveListItemFlexColumnRenderer": {
                  "text": {
                    "runs": [
                      { "text": "$subtitle" },
                      { "text": " • " },
                      { "text": "Album Name" },
                      { "text": " • " },
                      { "text": "$duration" }
                    ]
                  }
                }
              }
            ],
            "thumbnail": {
              "musicThumbnailRenderer": {
                "thumbnail": {
                  "thumbnails": [
                    { "url": "https://example.com/art=w60-h60", "width": 60, "height": 60 }
                  ]
                }
              }
            },
            "playlistItemData": { "videoId": "$videoId" }
          }
        }
    """.trimIndent()

    @Test
    fun `browse shelves parse carousel and song shelf`() {
        val root = JSONObject(
            """
            {
              "contents": {
                "singleColumnBrowseResultsRenderer": {
                  "tabs": [{
                    "tabRenderer": {
                      "content": {
                        "sectionListRenderer": {
                          "contents": [
                            {
                              "musicCarouselShelfRenderer": {
                                "header": {
                                  "musicCarouselShelfBasicHeaderRenderer": {
                                    "title": { "runs": [{ "text": "Made for you" }] }
                                  }
                                },
                                "contents": [{
                                  "musicTwoRowItemRenderer": {
                                    "title": { "runs": [{ "text": "Chill Mix" }] },
                                    "subtitle": { "runs": [{ "text": "Playlist" }] },
                                    "navigationEndpoint": {
                                      "browseEndpoint": {
                                        "browseId": "VLPL123",
                                        "browseEndpointContextSupportedConfigs": {
                                          "browseEndpointContextMusicConfig": {
                                            "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
                                          }
                                        }
                                      }
                                    }
                                  }
                                }]
                              }
                            },
                            {
                              "musicShelfRenderer": {
                                "title": { "runs": [{ "text": "Songs" }] },
                                "contents": [
                                  ${track("video12345", "Midnight City", "M83")}
                                ]
                              }
                            }
                          ]
                        }
                      }
                    }
                  }]
                }
              }
            }
            """.trimIndent()
        )

        val shelves = YouTubeParser.shelves(root)
        assertEquals(2, shelves.size)
        assertEquals("Made for you", shelves[0].title)
        assertTrue(shelves[0].items[0] is YouTubeItem.Playlist)
        assertEquals("Songs", shelves[1].title)
        val song = (shelves[1].items[0] as YouTubeItem.Track).song
        assertEquals("Midnight City", song.title)
        assertEquals("M83", song.artist)
        assertEquals("video12345", song.videoId)
        assertEquals(222_000L, song.duration)
    }

    @Test
    fun `continuation contents parse rows and next token`() {
        val root = JSONObject(
            """
            {
              "continuationContents": {
                "musicShelfContinuation": {
                  "contents": [
                    ${track("video99999", "Nightcall", "Kavinsky")}
                  ],
                  "continuations": [{
                    "nextContinuationData": { "continuation": "next-token" }
                  }]
                }
              }
            }
            """.trimIndent()
        )

        val shelves = YouTubeParser.continuationShelves(root)
        assertEquals(1, shelves.size)
        assertEquals(1, shelves.first().items.size)
        assertEquals("next-token", YouTubeParser.continuation(root))
    }

    @Test
    fun `playlist page reads microformat title and fixed-column duration`() {
        val root = JSONObject(
            """
            {
              "microformat": {
                "microformatDataRenderer": {
                  "title": "Uncut Bollywood",
                  "description": "Playlist • YouTube Music",
                  "thumbnail": {
                    "thumbnails": [
                      { "url": "https://example.com/art=w544-h544", "width": 544, "height": 544 }
                    ]
                  }
                }
              },
              "contents": {
                "twoColumnBrowseResultsRenderer": {
                  "secondaryContents": {
                    "sectionListRenderer": {
                      "contents": [{
                        "musicPlaylistShelfRenderer": {
                          "contents": [{
                            "musicResponsiveListItemRenderer": {
                              "flexColumns": [
                                {
                                  "musicResponsiveListItemFlexColumnRenderer": {
                                    "text": { "runs": [{ "text": "Gehra Hua" }] }
                                  }
                                },
                                {
                                  "musicResponsiveListItemFlexColumnRenderer": {
                                    "text": { "runs": [{ "text": "Arijit Singh" }] }
                                  }
                                }
                              ],
                              "fixedColumns": [{
                                "musicResponsiveListItemFixedColumnRenderer": {
                                  "text": { "runs": [{ "text": "3:51" }] }
                                }
                              }],
                              "playlistItemData": { "videoId": "GX9x62kFsVU" }
                            }
                          }]
                        }
                      }]
                    }
                  }
                }
              }
            }
            """.trimIndent()
        )

        val page = YouTubeParser.page(
            "VLRDCLAK5uy_krbBs7P2iEb30IODyVbiOXWyhZtAIX9Uk",
            root
        )
        assertEquals("Uncut Bollywood", page.title)
        assertEquals("VLRDCLAK5uy_krbBs7P2iEb30IODyVbiOXWyhZtAIX9Uk", page.browseId)
        assertTrue(page.isPlaylist)
        val song = page.songs.first()
        assertEquals("Gehra Hua", song.title)
        assertEquals(231_000L, song.duration)
    }

    @Test
    fun `mood category page reads bare music header title`() {
        val root = JSONObject(
            """
            {
              "header": {
                "musicHeaderRenderer": {
                  "title": { "runs": [{ "text": "Chill" }] }
                }
              },
              "contents": {
                "singleColumnBrowseResultsRenderer": {
                  "tabs": [{
                    "tabRenderer": {
                      "content": {
                        "sectionListRenderer": {
                          "contents": [{
                            "musicCarouselShelfRenderer": {
                              "contents": [{
                                "musicTwoRowItemRenderer": {
                                  "title": { "runs": [{ "text": "Coffee Shop Blend" }] },
                                  "subtitle": { "runs": [{ "text": "Playlist" }] },
                                  "navigationEndpoint": {
                                    "browseEndpoint": { "browseId": "VLPLchill" }
                                  }
                                }
                              }]
                            }
                          }]
                        }
                      }
                    }
                  }]
                }
              }
            }
            """.trimIndent()
        )

        val page = YouTubeParser.page("FEmusic_moods_and_genres_category", root)
        assertEquals("Chill", page.title)
        assertEquals(1, page.shelves.size)
    }

    @Test
    fun `album page fills artist and album from microformat subtitle`() {
        val root = JSONObject(
            """
            {
              "microformat": {
                "microformatDataRenderer": {
                  "title": "MIDNIGHT IN DIEPKLOOF ZONE 2",
                  "description": "Album • Tman Xpress and Mellow & Sleazy"
                }
              },
              "contents": {
                "twoColumnBrowseResultsRenderer": {
                  "secondaryContents": {
                    "sectionListRenderer": {
                      "contents": [{
                        "musicShelfRenderer": {
                          "contents": [{
                            "musicResponsiveListItemRenderer": {
                              "flexColumns": [
                                {
                                  "musicResponsiveListItemFlexColumnRenderer": {
                                    "text": { "runs": [{ "text": "Sifela Amanyala" }] }
                                  }
                                },
                                {
                                  "musicResponsiveListItemFlexColumnRenderer": {
                                    "text": {}
                                  }
                                },
                                {
                                  "musicResponsiveListItemFlexColumnRenderer": {
                                    "text": { "runs": [{ "text": "717K plays" }] }
                                  }
                                }
                              ],
                              "fixedColumns": [{
                                "musicResponsiveListItemFixedColumnRenderer": {
                                  "text": { "runs": [{ "text": "7:22" }] }
                                }
                              }],
                              "playlistItemData": { "videoId": "ptFQ1n2VNoU" }
                            }
                          }]
                        }
                      }]
                    }
                  }
                }
              }
            }
            """.trimIndent()
        )

        val page = YouTubeParser.page("MPREb_KOmBDPwlM9T", root)
        assertTrue(page.isAlbum)
        assertEquals("MIDNIGHT IN DIEPKLOOF ZONE 2", page.title)
        val song = page.songs.first()
        assertEquals("Sifela Amanyala", song.title)
        assertEquals("Tman Xpress and Mellow & Sleazy", song.artist)
        assertEquals("MIDNIGHT IN DIEPKLOOF ZONE 2", song.album)
        assertEquals(442_000L, song.duration)
    }

    @Test
    fun `search page exposes top result and shelf items`() {
        val root = JSONObject(
            """
            {
              "contents": {
                "tabbedSearchResultsRenderer": {
                  "tabs": [{
                    "tabRenderer": {
                      "content": {
                        "sectionListRenderer": {
                          "contents": [
                            {
                              "musicCardShelfRenderer": {
                                "title": { "runs": [{ "text": "Midnight City" }] },
                                "subtitle": { "runs": [{ "text": "M83" }] },
                                "onTap": { "watchEndpoint": { "videoId": "topVideo" } },
                                "thumbnail": {
                                  "musicThumbnailRenderer": {
                                    "thumbnail": {
                                      "thumbnails": [{
                                        "url": "https://example.com/art=w60-h60",
                                        "width": 60,
                                        "height": 60
                                      }]
                                    }
                                  }
                                }
                              }
                            },
                            {
                              "musicShelfRenderer": {
                                "contents": [
                                  ${track("video12345", "Midnight City", "M83")}
                                ]
                              }
                            }
                          ]
                        }
                      }
                    }
                  }]
                }
              }
            }
            """.trimIndent()
        )

        val page = YouTubeParser.search("Midnight City", root, com.moodtunes.app.domain.model.YouTubeSearchFilter.SONGS)
        assertEquals("Midnight City", page.top?.title)
        assertEquals(1, page.sections.flatMap { it.items }.size)
    }

    @Test
    fun `playlist whose title contains album is not treated as an album`() {
        val root = JSONObject(
            """
            {
              "microformat": {
                "microformatDataRenderer": {
                  "title": "Album Covers Live",
                  "description": "Playlist • YouTube Music"
                }
              },
              "contents": {
                "twoColumnBrowseResultsRenderer": {
                  "secondaryContents": {
                    "sectionListRenderer": {
                      "contents": [{
                        "musicPlaylistShelfRenderer": {
                          "contents": [{
                            "musicResponsiveListItemRenderer": {
                              "flexColumns": [
                                {
                                  "musicResponsiveListItemFlexColumnRenderer": {
                                    "text": { "runs": [{ "text": "Track One" }] }
                                  }
                                },
                                {
                                  "musicResponsiveListItemFlexColumnRenderer": {
                                    "text": { "runs": [{ "text": "Artist One" }] }
                                  }
                                }
                              ],
                              "playlistItemData": { "videoId": "video-one" }
                            }
                          }]
                        }
                      }]
                    }
                  }
                }
              }
            }
            """.trimIndent()
        )

        val page = YouTubeParser.page("VLPLalbumcovers", root)
        assertFalse(page.isAlbum)
        assertTrue(page.isPlaylist)
        assertEquals("Artist One", page.songs.first().artist)
    }

    @Test
    fun `play counts parse with and without a multiplier`() {
        assertEquals(717L, parsePlayCount("717 plays"))
        assertEquals(1_250_000L, parsePlayCount("1.25M plays"))
        assertEquals(3_400_000_000L, parsePlayCount("3.4B views"))
        assertNull(parsePlayCount("No plays here"))
    }
}
