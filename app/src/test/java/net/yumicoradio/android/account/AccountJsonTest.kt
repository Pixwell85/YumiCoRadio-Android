// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.account

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountJsonTest {
    @Test fun `parses bounded Android session`() {
        val value = Json.parseToJsonElement("""{
          "accountId":"00000000-0000-0000-0000-000000000001",
          "sessionId":"00000000-0000-0000-0000-000000000002",
          "username":"Shiro","role":"admin","moderator":false,"clientType":"android",
          "deviceLabel":"Samsung S25+","createdAtMs":1,"lastUsedAtMs":2,
          "idleExpiresAtMs":3,"absoluteExpiresAtMs":4
        }""")
        val session = parseSession(value)
        assertEquals("Shiro", session.username)
        assertEquals("admin", session.role)
    }

    @Test fun `rejects browser session for Android token`() {
        val value = Json.parseToJsonElement("""{
          "accountId":"00000000-0000-0000-0000-000000000001",
          "sessionId":"00000000-0000-0000-0000-000000000002",
          "username":"Shiro","role":"admin","moderator":false,"clientType":"browser",
          "deviceLabel":"Web browser","createdAtMs":1,"lastUsedAtMs":2,
          "idleExpiresAtMs":3,"absoluteExpiresAtMs":4
        }""")
        assertTrue(runCatching { parseSession(value) }.exceptionOrNull() is AccountApiException)
    }

    @Test fun `accepts claimed voice account session and profile`() {
        val session = parseSession(Json.parseToJsonElement("""{
          "accountId":"eade1cf3-c29c-44a6-8ccb-4e3672da54b5",
          "sessionId":"4384a86c-9dad-4c84-bfe7-1fa2ac907478",
          "username":"WinDark99","role":"voice","moderator":false,"clientType":"android",
          "deviceLabel":"Xiaomi 2211133G","createdAtMs":1,"lastUsedAtMs":2,
          "idleExpiresAtMs":3,"absoluteExpiresAtMs":4
        }"""))
        val profile = parseProfile(Json.parseToJsonElement("""{
          "accountId":"eade1cf3-c29c-44a6-8ccb-4e3672da54b5",
          "username":"WinDark99","email":"test@example.com","role":"voice","moderator":false,
          "createdAtMs":1,"recoveryCodesRemaining":0,"activeSessions":2
        }"""))
        assertEquals("voice", session.role)
        assertEquals("voice", profile.role)
    }

    @Test fun `accepts voice accounts in administrator listing`() {
        val page = parseAdminPage(Json.parseToJsonElement("""{
          "page":1,"pageSize":20,"totalRows":1,"totalPages":1,
          "rows":[{
            "accountId":"eade1cf3-c29c-44a6-8ccb-4e3672da54b5",
            "username":"WinDark99","role":"voice","status":"active",
            "moderator":false,"createdAtMs":1
          }]
        }"""))
        assertEquals("voice", page.rows.single().role)
    }

    @Test fun `validates one-use chat ticket shape`() {
        val ticket = parseChatTicket(Json.parseToJsonElement(
            """{"ticket":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","expiresAtMs":99}"""))
        assertEquals(43, ticket.ticket.length)
    }
}
