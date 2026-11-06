package com.fenakhay.polyglossa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The library reads as Java expects: static lookups, boolean getters named is…, and nulls for misses. */
class JavaApiTest {
    @Test
    void lookupsAreStatic() {
        Language english = Language.of("en");
        assertEquals("English", english.getName());
        assertNull(Language.get("xx-none"));
        assertTrue(Language.contains("ang"));
        assertEquals("ang", Language.named("Old English").getCode());
        assertFalse(Language.getAll().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> Language.of("xx-none"));
    }

    @Test
    void familiesAndScriptsAreStatic() {
        LanguageFamily germanic = LanguageFamily.of("gmw");
        assertEquals("gem", germanic.getParent().getCode());
        assertNull(LanguageFamily.get("xx-none"));
        assertEquals("Arab", Script.named("Arabic").getCode());
        assertEquals("Grek", Script.detect("Ελληνικά").getCode());
        assertTrue(Script.of("Cyrl").contains(0x416));
    }

    @Test
    void booleansReadAsIs() {
        assertTrue(Language.of("en-US").isEtymologyOnly());
        assertFalse(Language.of("en").isEtymologyOnly());
    }

    @Test
    void theBundledCatalogIsStatic() {
        Catalog catalog = Catalog.getBUNDLED();
        assertNotNull(catalog.getDate());
        assertEquals("Hant", catalog.get("zh").detectScript("語言").getCode());
        Catalog copy = Catalog.fromTsv(catalog.toTsv());
        assertEquals(catalog.getLanguages().size(), copy.getLanguages().size());
        List<Script> scripts = Language.of("en").getScripts();
        assertEquals("Latn", scripts.get(0).getCode());
    }
}
