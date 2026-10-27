package com.folio.app.core.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceAppsTest {
    @Test fun packagesAndAuthorities() {
        assertEquals("WhatsApp", SourceApps.label("com.whatsapp"))
        assertEquals("WhatsApp", SourceApps.label("com.whatsapp.provider.media"))
        assertEquals("WhatsApp", SourceApps.label("com.whatsapp.w4b.provider.media"))
        assertEquals("Gmail", SourceApps.label("com.google.android.gm.sapi"))
        assertEquals("Files", SourceApps.label("com.android.providers.downloads.documents"))
        assertEquals("Files", SourceApps.label("media"))
    }

    @Test fun unknownIsNull() {
        assertNull(SourceApps.label(null))
        assertNull(SourceApps.label(""))
        assertNull(SourceApps.label("com.example.app"))
        assertNull(SourceApps.label("com.whatsappfake"))
    }
}
