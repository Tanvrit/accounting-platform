package com.tanvrit.accounting.screens.itcWorkspace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ItcImportParserTest {
    @Test
    fun `parses GSTR-2B offline-tool JSON shape (data-docdata-b2b)`() {
        val json =
            """
            {"data":{"docdata":{"b2b":[
              {"ctin":"27ABCDE1234F1Z5","inv":[
                {"inum":"INV-22","dt":"01-04-2026","txval":"10000.00","igst":"1800.00"}
              ]}
            ]}}}
            """.trimIndent()
        val result = ItcImportParser.parse(json)
        assertEquals("JSON", result.detectedFormat)
        assertEquals(1, result.rows.size)
        val row = result.rows.first()
        assertEquals("27ABCDE1234F1Z5", row.supplierGstin)
        assertEquals("INV-22", row.invoiceNumber)
        assertEquals(1_000_000L, row.taxableValueMinorUnits) // ₹10,000
        assertEquals(180_000L, row.igstMinorUnits) // ₹1,800
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `parses CSV with alias header and Indian-grouping numbers`() {
        val csv =
            "Supplier GSTIN,Invoice Number,Invoice Date,Taxable Value,IGST,CGST,SGST\n" +
                "27ABCDE1234F1Z5,INV-1,2026-04-01,\"1,50,000.00\",0,13500,13500\n"
        val result = ItcImportParser.parse(csv)
        assertEquals("CSV", result.detectedFormat)
        assertEquals(1, result.rows.size)
        assertEquals(150_000_00L, result.rows[0].taxableValueMinorUnits)
        assertEquals(2_700_000L, result.rows[0].itcMinorUnits)
    }

    @Test
    fun `parses TSV via delimiter sniffing and headerless positional fallback`() {
        val tsv =
            "27ABCDE1234F1Z5\tINV-9\t2026-05-02\t500.00\t90\t0\t0\t590\n"
        val result = ItcImportParser.parse(tsv)
        assertEquals("TSV", result.detectedFormat)
        assertEquals(1, result.rows.size)
        assertEquals("INV-9", result.rows[0].invoiceNumber)
        assertEquals(50_000L, result.rows[0].taxableValueMinorUnits)
    }

    @Test
    fun `row-level errors carry row numbers, duplicates flagged`() {
        val csv =
            "supplier gstin,invoice number,date,taxable,igst,cgst,sgst\n" +
                "27ABCDE1234F1Z5,INV-1,2026-04-01,100,9,0,0\n" +
                "27ABCDE1234F1Z5,INV-1,2026-04-05,200,36,0,0\n" +
                "27ABCDE1234F1Z5,,2026-04-05,200,36,0,0\n"
        val result = ItcImportParser.parse(csv)
        assertEquals(2, result.rows.size)
        assertTrue(result.errors.any { it.contains("Row 4") && it.contains("invoice number missing") })
        assertTrue(result.errors.any { it.contains("duplicate") })
    }

    @Test
    fun `parseMinor is exact minor-unit math incl negatives and padding`() {
        assertEquals(150_000_00L, ItcImportParser.parseMinor("1,50,000.00"))
        assertEquals(-590L, ItcImportParser.parseMinor("(5.90)"))
        assertEquals(-590L, ItcImportParser.parseMinor("-5.9"))
        assertEquals(5L, ItcImportParser.parseMinor("0.05"))
        assertEquals(0L, ItcImportParser.parseMinor(""))
        assertEquals(0L, ItcImportParser.parseMinor("abc"))
        // Third decimal truncates (documented — supplier rounding is not an ITC claim)
        assertEquals(123L, ItcImportParser.parseMinor("1.234"))
    }

    @Test
    fun `empty and wrong-shape payloads fail honestly`() {
        assertTrue(ItcImportParser.parse("").errors.isNotEmpty())
        assertTrue(
            ItcImportParser
                .parse("{}")
                .errors
                .first()
                .contains("b2b"),
        )
        assertEquals(0, ItcImportParser.parse("hello\nworld").rows.size)
    }
}
