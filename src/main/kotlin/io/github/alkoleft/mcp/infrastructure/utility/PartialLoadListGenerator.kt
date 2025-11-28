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

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.walk

private val logger = KotlinLogging.logger { }

/**
 * Генератор списка файлов для частичной загрузки конфигурации.
 *
 * Преобразует набор измененных файлов в список для параметра -listFile
 * команды /LoadConfigFromFiles с учетом специфики 1С:
 * - Для BSL файлов добавляет связанные XML объектов метаданных
 * - Преобразует абсолютные пути в относительные от каталога конфигурации
 */
@Component
class PartialLoadListGenerator {
    /**
     * Типы метаданных 1С и их каталоги
     */
    private val metadataTypes = setOf(
        "Catalogs",
        "Documents",
        "DataProcessors",
        "Reports",
        "InformationRegisters",
        "AccumulationRegisters",
        "AccountingRegisters",
        "CalculationRegisters",
        "ChartsOfCharacteristicTypes",
        "ChartsOfAccounts",
        "ChartsOfCalculationTypes",
        "ExchangePlans",
        "BusinessProcesses",
        "Tasks",
        "Constants",
        "Enums",
        "CommonModules",
        "CommonForms",
        "CommonCommands",
        "CommonTemplates",
        "CommonPictures",
        "SessionParameters",
        "Roles",
        "CommonAttributes",
        "ExternalDataSources",
        "FilterCriteria",
        "EventSubscriptions",
        "ScheduledJobs",
        "FunctionalOptions",
        "FunctionalOptionsParameters",
        "DefinedTypes",
        "SettingsStorages",
        "Sequences",
        "DocumentJournals",
        "WebServices",
        "HTTPServices",
        "WSReferences",
        "Styles",
        "StyleItems",
        "Languages",
        "Subsystems",
        "CommandGroups",
        "Interfaces",
        "XDTOPackages",
    )

    /**
     * Генерирует файл со списком для частичной загрузки.
     *
     * @param sourceSetPath Базовый путь к source set (каталог конфигурации)
     * @param changedFiles Набор абсолютных путей к измененным файлам
     * @return Путь к временному файлу со списком
     */
    fun generate(
        sourceSetPath: Path,
        changedFiles: Set<Path>,
    ): Path {
        logger.debug { "Генерация списка файлов для частичной загрузки: ${changedFiles.size} файлов" }

        val filesToLoad = mutableSetOf<String>()

        changedFiles.forEach { absolutePath ->
            processChangedFile(sourceSetPath, absolutePath, filesToLoad)
        }

        logger.info { "Подготовлено ${filesToLoad.size} файлов для частичной загрузки" }

        return createListFile(filesToLoad)
    }

    /**
     * Обрабатывает измененный файл и добавляет необходимые пути в список загрузки.
     */
    private fun processChangedFile(
        sourceSetPath: Path,
        absolutePath: Path,
        filesToLoad: MutableSet<String>,
    ) {
        if (!absolutePath.startsWith(sourceSetPath)) {
            logger.warn { "Файл $absolutePath не принадлежит source set $sourceSetPath" }
            return
        }

        val relativePath = sourceSetPath.relativize(absolutePath)
        val relativePathStr = relativePath.toString().replace("\\", "/")

        when {
            absolutePath.extension.equals("bsl", ignoreCase = true) -> {
                processBslFile(sourceSetPath, relativePath, filesToLoad)
            }
            absolutePath.extension.equals("xml", ignoreCase = true) -> {
                filesToLoad.add(relativePathStr)
                logger.trace { "Добавлен XML: $relativePathStr" }
            }
            else -> {
                filesToLoad.add(relativePathStr)
                logger.trace { "Добавлен файл: $relativePathStr" }
            }
        }
    }

    /**
     * Обрабатывает BSL файл и добавляет связанные файлы объекта метаданных.
     *
     * При изменении BSL модуля необходимо загрузить:
     * 1. XML файл объекта метаданных
     * 2. Все файлы из каталога Ext объекта (включая сам BSL)
     */
    private fun processBslFile(
        sourceSetPath: Path,
        relativePath: Path,
        filesToLoad: MutableSet<String>,
    ) {
        val pathParts = relativePath.toList().map { it.toString() }

        if (pathParts.isEmpty()) {
            return
        }

        // Определяем тип метаданных и имя объекта
        val metadataInfo = findMetadataInfo(pathParts)

        if (metadataInfo != null) {
            val (metadataType, objectName, objectIndex) = metadataInfo

            // Добавляем XML объекта
            val xmlPath = "$metadataType/$objectName.xml"
            filesToLoad.add(xmlPath)
            logger.trace { "Добавлен XML объекта для BSL: $xmlPath" }

            // Добавляем все файлы из каталога объекта
            val objectPath = sourceSetPath.resolve(metadataType).resolve(objectName)
            if (objectPath.exists() && objectPath.isDirectory()) {
                addAllFilesFromDirectory(sourceSetPath, objectPath, filesToLoad)
            }
        } else {
            // Если не удалось определить объект, добавляем сам файл
            val relativePathStr = relativePath.toString().replace("\\", "/")
            filesToLoad.add(relativePathStr)
            logger.trace { "Добавлен BSL без определения объекта: $relativePathStr" }
        }
    }

    /**
     * Информация о метаданных: (тип, имя объекта, индекс в пути)
     */
    private data class MetadataInfo(
        val type: String,
        val objectName: String,
        val pathIndex: Int,
    )

    /**
     * Находит информацию о метаданных по пути файла.
     */
    private fun findMetadataInfo(pathParts: List<String>): MetadataInfo? {
        for (i in pathParts.indices) {
            if (pathParts[i] in metadataTypes && i + 1 < pathParts.size) {
                return MetadataInfo(
                    type = pathParts[i],
                    objectName = pathParts[i + 1],
                    pathIndex = i,
                )
            }
        }
        return null
    }

    /**
     * Добавляет все файлы из каталога рекурсивно.
     */
    private fun addAllFilesFromDirectory(
        sourceSetPath: Path,
        directory: Path,
        filesToLoad: MutableSet<String>,
    ) {
        try {
            directory.walk()
                .filter { Files.isRegularFile(it) }
                .forEach { file ->
                    val relativePath = sourceSetPath.relativize(file).toString().replace("\\", "/")
                    filesToLoad.add(relativePath)
                    logger.trace { "Добавлен связанный файл: $relativePath" }
                }
        } catch (e: Exception) {
            logger.warn(e) { "Ошибка при сканировании каталога: $directory" }
        }
    }

    /**
     * Создает временный файл со списком файлов для загрузки.
     */
    private fun createListFile(files: Set<String>): Path {
        val listFile = Files.createTempFile("partial-load-", ".txt")

        Files.write(listFile, files.sorted())

        logger.debug { "Создан файл списка: $listFile с ${files.size} записями" }

        return listFile
    }

    /**
     * Проверяет, содержат ли изменения файл Configuration.xml.
     * Если да, рекомендуется полная загрузка.
     */
    fun hasConfigurationXmlChanges(
        sourceSetPath: Path,
        changedFiles: Set<Path>,
    ): Boolean {
        return changedFiles.any { file ->
            val relativePath = if (file.startsWith(sourceSetPath)) {
                sourceSetPath.relativize(file)
            } else {
                file
            }
            relativePath.name.equals("Configuration.xml", ignoreCase = true)
        }
    }

    /**
     * Определяет, следует ли использовать частичную загрузку.
     *
     * @param changedFiles Набор измененных файлов
     * @param threshold Порог количества файлов для переключения на полную загрузку
     * @param sourceSetPath Базовый путь к source set
     * @return true если следует использовать частичную загрузку
     */
    fun shouldUsePartialLoad(
        changedFiles: Set<Path>,
        threshold: Int,
        sourceSetPath: Path,
    ): Boolean {
        if (changedFiles.size >= threshold) {
            logger.info { "Количество изменений (${changedFiles.size}) >= порога ($threshold), используется полная загрузка" }
            return false
        }

        if (hasConfigurationXmlChanges(sourceSetPath, changedFiles)) {
            logger.info { "Обнаружены изменения в Configuration.xml, используется полная загрузка" }
            return false
        }

        logger.info { "Используется частичная загрузка для ${changedFiles.size} файлов" }
        return true
    }
}

