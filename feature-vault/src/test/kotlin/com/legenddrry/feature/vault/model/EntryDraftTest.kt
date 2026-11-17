package com.legenddrry.feature.vault.model

import com.legenddrry.feature.vault.testing.entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryDraftTest {

    private val valid = EntryDraft(title = "Mail", password = "secret", notes = "note")

    @Test fun validDraftHasNoErrors() = assertTrue(valid.validate().isEmpty())

    @Test fun blankTitleAndPassword() {
        val errors = EntryDraft().validate()
        assertEquals("Title is required", errors[DraftField.TITLE])
        assertEquals("Password is required", errors[DraftField.PASSWORD])
        assertEquals(DraftField.TITLE, errors.keys.first())
    }

    @Test fun whitespaceOnlyTitleAndPasswordAreInvalid() {
        val errors = EntryDraft(title = "   ", password = " \t ").validate()
        assertTrue(DraftField.TITLE in errors)
        assertTrue(DraftField.PASSWORD in errors)
    }

    @Test fun passwordWithSurroundingSpacesIsValidAndKept() {
        val d = valid.copy(password = " pw ")
        assertTrue(d.validate().isEmpty())
        assertEquals(" pw ", d.normalized().password)
    }

    @Test fun titleLengthLimit() {
        assertTrue(valid.copy(title = "a".repeat(100)).validate().isEmpty())
        assertTrue(valid.copy(title = "  " + "a".repeat(100) + "  ").validate().isEmpty())
        assertTrue(DraftField.TITLE in valid.copy(title = "a".repeat(101)).validate())
    }

    @Test fun notesLengthLimit() {
        assertTrue(valid.copy(notes = "n".repeat(1000)).validate().isEmpty())
        assertTrue(DraftField.NOTES in valid.copy(notes = "n".repeat(1001)).validate())
    }

    @Test fun blankNotesAreInvalid() {
        assertEquals("Notes are required", valid.copy(notes = "").validate()[DraftField.NOTES])
        assertEquals("Notes are required", valid.copy(notes = "   ").validate()[DraftField.NOTES])
    }

    @Test fun websiteValidation() {
        val ok = listOf(
            "", "   ", "example.com", "https://example.com", "HTTP://sub.example.co.uk/path?q=1",
            "localhost", "http://localhost:8080/x", "  example.com  ", "user@example.com:443",
        )
        val bad = listOf("example", "exa mple.com", "http://", "https://nodot", ".com", "example.", "a..b", "ftp//x")
        ok.forEach { assertFalse("expected valid: '$it'", DraftField.WEBSITE in valid.copy(website = it).validate()) }
        bad.forEach {
            assertEquals("expected invalid: '$it'", "Enter a valid website", valid.copy(website = it).validate()[DraftField.WEBSITE])
        }
    }

    @Test fun blankCategoryNormalizesToDefault() {
        val d = valid.copy(category = "  ")
        assertTrue(d.validate().isEmpty())
        assertEquals(DEFAULT_CATEGORY, d.normalized().category)
    }

    @Test fun normalizationTrimsSomeFieldsOnly() {
        val n = EntryDraft(
            title = " T ", website = " w.com ", username = " u ", password = " p ", notes = " n ", category = " Work ",
        ).normalized()
        assertEquals(EntryDraft(title = "T", website = "w.com", username = "u", password = " p ", notes = " n ", category = "Work"), n)
    }

    @Test fun toEntryUsesNewIdOnlyForNewDrafts() {
        val e1 = valid.copy(title = " Mail ").toEntry("new-1", 42L)
        assertEquals("new-1", e1.id)
        assertEquals("Mail", e1.title)
        assertEquals(42L, e1.updatedAtEpochMs)
        val e2 = valid.copy(id = "existing").toEntry("new-1", 7L)
        assertEquals("existing", e2.id)
        assertEquals(7L, e2.updatedAtEpochMs)
    }

    @Test fun roundTrip() {
        val e = entry("x", title = "Bank", website = "bank.com", username = "me", password = "pw", notes = "n", category = "Banking", updatedAtEpochMs = 99)
        val d = e.toDraft()
        assertEquals("x", d.id)
        assertEquals(e, d.toEntry("ignored", 99))
    }
}
