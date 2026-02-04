/*
 * This file is part of METR.
 *
 * Copyright (C) 2025 Aleksey Koryakin <alkoleft@gmail.com> and contributors.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * METR is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * METR is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with METR.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.alkoleft.mcp.infrastructure.utility

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PartialLoadListGeneratorTest {
    private lateinit var generator: PartialLoadListGenerator

    @BeforeEach
    fun setUp() {
        generator = PartialLoadListGenerator()
    }

    @Test
    fun `should generate list file with XML files`(
        @TempDir tempDir: Path,
    ) {
        // Arrange
        val sourceSetPath = tempDir.resolve("config")
        Files.createDirectories(sourceSetPath.resolve("Catalogs"))
        val xmlFile = sourceSetPath.resolve("Catalogs/Catalog1.xml")
        Files.writeString(xmlFile, "<catalog/>")

        val changedFiles = setOf(xmlFile)

        // Act
        val listFile = generator.generate(sourceSetPath, changedFiles)

        // Assert
        assertTrue(Files.exists(listFile))
        val content = Files.readAllLines(listFile)
        assertEquals(1, content.size)
        assertEquals("Catalogs/Catalog1.xml", content[0])
    }

    @Test
    fun `should add XML object when BSL file changed`(
        @TempDir tempDir: Path,
    ) {
        // Arrange
        val sourceSetPath = tempDir.resolve("config")
        val catalogDir = sourceSetPath.resolve("Catalogs/Справочник1")
        val extDir = catalogDir.resolve("Ext")
        Files.createDirectories(extDir)

        // Создаем файлы
        Files.writeString(sourceSetPath.resolve("Catalogs/Справочник1.xml"), "<catalog/>")
        val bslFile = extDir.resolve("ObjectModule.bsl")
        Files.writeString(bslFile, "// Module code")

        val changedFiles = setOf(bslFile)

        // Act
        val listFile = generator.generate(sourceSetPath, changedFiles)

        // Assert
        assertTrue(Files.exists(listFile))
        val content = Files.readAllLines(listFile)

        // Должен содержать XML объекта и BSL модуль
        assertTrue(content.any { it == "Catalogs/Справочник1.xml" })
        assertTrue(content.any { it == "Catalogs/Справочник1/Ext/ObjectModule.bsl" })
    }

    @Test
    fun `should add all related files from object directory when BSL changed`(
        @TempDir tempDir: Path,
    ) {
        // Arrange
        val sourceSetPath = tempDir.resolve("config")
        val documentDir = sourceSetPath.resolve("Documents/Документ1")
        val extDir = documentDir.resolve("Ext")
        val formsDir = documentDir.resolve("Forms/ФормаДокумента")
        Files.createDirectories(extDir)
        Files.createDirectories(formsDir.resolve("Ext"))

        // Создаем файлы объекта
        Files.writeString(sourceSetPath.resolve("Documents/Документ1.xml"), "<document/>")
        Files.writeString(extDir.resolve("ObjectModule.bsl"), "// Object module")
        Files.writeString(extDir.resolve("ManagerModule.bsl"), "// Manager module")
        Files.writeString(formsDir.resolve("Ext/Form.xml"), "<form/>")
        Files.writeString(formsDir.resolve("Ext/Form.bsl"), "// Form module")

        // Изменили только ObjectModule.bsl
        val changedFiles = setOf(extDir.resolve("ObjectModule.bsl"))

        // Act
        val listFile = generator.generate(sourceSetPath, changedFiles)

        // Assert
        val content = Files.readAllLines(listFile)

        // Должен содержать все файлы объекта
        assertTrue(content.any { it == "Documents/Документ1.xml" })
        assertTrue(content.any { it.contains("ObjectModule.bsl") })
        assertTrue(content.any { it.contains("ManagerModule.bsl") })
        assertTrue(content.any { it.contains("Form.xml") })
        assertTrue(content.any { it.contains("Form.bsl") })
    }

    @Test
    fun `should detect Configuration xml changes`(
        @TempDir tempDir: Path,
    ) {
        // Arrange
        val sourceSetPath = tempDir.resolve("config")
        Files.createDirectories(sourceSetPath)
        val configXml = sourceSetPath.resolve("Configuration.xml")
        Files.writeString(configXml, "<configuration/>")

        val changedFilesWithConfig = setOf(configXml)
        val changedFilesWithoutConfig = setOf(sourceSetPath.resolve("Catalogs/Catalog1.xml"))

        // Act & Assert
        assertTrue(generator.hasConfigurationXmlChanges(sourceSetPath, changedFilesWithConfig))
        assertFalse(generator.hasConfigurationXmlChanges(sourceSetPath, changedFilesWithoutConfig))
    }

    @Test
    fun `should use partial load when under threshold and no Configuration xml changes`(
        @TempDir tempDir: Path,
    ) {
        // Arrange
        val sourceSetPath = tempDir.resolve("config")
        Files.createDirectories(sourceSetPath.resolve("Catalogs"))
        val changedFiles = (1..10).map {
            val file = sourceSetPath.resolve("Catalogs/Catalog$it.xml")
            Files.writeString(file, "<catalog/>")
            file
        }.toSet()

        // Act & Assert
        assertTrue(generator.shouldUsePartialLoad(changedFiles, 20, sourceSetPath))
        assertFalse(generator.shouldUsePartialLoad(changedFiles, 5, sourceSetPath))
    }

    @Test
    fun `should not use partial load when Configuration xml is changed`(
        @TempDir tempDir: Path,
    ) {
        // Arrange
        val sourceSetPath = tempDir.resolve("config")
        Files.createDirectories(sourceSetPath)
        val configXml = sourceSetPath.resolve("Configuration.xml")
        Files.writeString(configXml, "<configuration/>")

        val changedFiles = setOf(configXml)

        // Act & Assert
        assertFalse(generator.shouldUsePartialLoad(changedFiles, 100, sourceSetPath))
    }

    @Test
    fun `should handle CommonModules BSL files`(
        @TempDir tempDir: Path,
    ) {
        // Arrange
        val sourceSetPath = tempDir.resolve("config")
        val moduleDir = sourceSetPath.resolve("CommonModules/ОбщийМодуль1")
        val extDir = moduleDir.resolve("Ext")
        Files.createDirectories(extDir)

        Files.writeString(sourceSetPath.resolve("CommonModules/ОбщийМодуль1.xml"), "<commonModule/>")
        val bslFile = extDir.resolve("Module.bsl")
        Files.writeString(bslFile, "// Common module code")

        val changedFiles = setOf(bslFile)

        // Act
        val listFile = generator.generate(sourceSetPath, changedFiles)

        // Assert
        val content = Files.readAllLines(listFile)
        assertTrue(content.any { it == "CommonModules/ОбщийМодуль1.xml" })
        assertTrue(content.any { it.contains("Module.bsl") })
    }

    @Test
    fun `should handle DataProcessors BSL files`(
        @TempDir tempDir: Path,
    ) {
        // Arrange
        val sourceSetPath = tempDir.resolve("config")
        val processorDir = sourceSetPath.resolve("DataProcessors/Обработка1")
        val extDir = processorDir.resolve("Ext")
        Files.createDirectories(extDir)

        Files.writeString(sourceSetPath.resolve("DataProcessors/Обработка1.xml"), "<dataProcessor/>")
        val bslFile = extDir.resolve("ObjectModule.bsl")
        Files.writeString(bslFile, "// Data processor module")

        val changedFiles = setOf(bslFile)

        // Act
        val listFile = generator.generate(sourceSetPath, changedFiles)

        // Assert
        val content = Files.readAllLines(listFile)
        assertTrue(content.any { it == "DataProcessors/Обработка1.xml" })
        assertTrue(content.any { it.contains("ObjectModule.bsl") })
    }

    @Test
    fun `should handle multiple changed files from different objects`(
        @TempDir tempDir: Path,
    ) {
        // Arrange
        val sourceSetPath = tempDir.resolve("config")

        // Catalog
        val catalogDir = sourceSetPath.resolve("Catalogs/Справочник1/Ext")
        Files.createDirectories(catalogDir)
        Files.writeString(sourceSetPath.resolve("Catalogs/Справочник1.xml"), "<catalog/>")
        val catalogBsl = catalogDir.resolve("ObjectModule.bsl")
        Files.writeString(catalogBsl, "// Catalog module")

        // Document
        val documentDir = sourceSetPath.resolve("Documents/Документ1/Ext")
        Files.createDirectories(documentDir)
        Files.writeString(sourceSetPath.resolve("Documents/Документ1.xml"), "<document/>")
        val documentBsl = documentDir.resolve("ObjectModule.bsl")
        Files.writeString(documentBsl, "// Document module")

        val changedFiles = setOf(catalogBsl, documentBsl)

        // Act
        val listFile = generator.generate(sourceSetPath, changedFiles)

        // Assert
        val content = Files.readAllLines(listFile)
        assertTrue(content.any { it == "Catalogs/Справочник1.xml" })
        assertTrue(content.any { it == "Documents/Документ1.xml" })
        assertTrue(content.any { it.contains("Catalogs/Справочник1/Ext/ObjectModule.bsl") })
        assertTrue(content.any { it.contains("Documents/Документ1/Ext/ObjectModule.bsl") })
    }

    @Test
    fun `should skip files outside source set path`(
        @TempDir tempDir: Path,
    ) {
        // Arrange
        val sourceSetPath = tempDir.resolve("config")
        Files.createDirectories(sourceSetPath)

        val outsideFile = tempDir.resolve("other/file.xml")
        Files.createDirectories(outsideFile.parent)
        Files.writeString(outsideFile, "<data/>")

        val changedFiles = setOf(outsideFile)

        // Act
        val listFile = generator.generate(sourceSetPath, changedFiles)

        // Assert
        val content = Files.readAllLines(listFile)
        assertTrue(content.isEmpty())
    }
}

