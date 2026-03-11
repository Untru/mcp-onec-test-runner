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

package io.github.alkoleft.mcp.infrastructure.changes

import io.github.oshai.kotlinlogging.KotlinLogging
import org.mapdb.DB
import org.mapdb.Serializer
import java.nio.file.Path

private val logger = KotlinLogging.logger { }

class ChangesStore(private val db: DB) {
    private val hashMap =
        db
            .hashMap("file_hashes")
            .keySerializer(Serializer.STRING)
            .valueSerializer(Serializer.STRING)
            .createOrOpen()

    fun isEmpty() = hashMap.isEmpty()

    fun getHash(file: Path): String? =
        try {
            val key = normalizeKey(file)
            hashMap[key]
        } catch (e: Exception) {
            logger.debug(e) { "Не удалось получить хеш для файла: $file" }
            null
        }

    fun batchUpdate(updates: Map<Path, String>) {
        if (updates.isEmpty()) return
        try {
            for ((file, hash) in updates) {
                val key = normalizeKey(file)
                hashMap[key] = hash
            }
            db.commit()
        } catch (e: Exception) {
            logger.error(e) { "Не удалось выполнить пакетное обновление хешей файлов" }
            db.rollback()
            throw e
        }
    }

    private fun normalizeKey(file: Path): String = file.toAbsolutePath().normalize().toString()

}