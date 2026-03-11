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

package io.github.alkoleft.mcp.infrastructure.storage

import io.github.alkoleft.mcp.configuration.properties.ProjectFormat
import io.github.alkoleft.mcp.configuration.properties.SourceSet

/**
 * Context for a specific source set that includes all necessary components
 * for change detection and hash storage.
 *
 * @param sourceSet The source set configuration
 * @param hashStorage The hash storage for this source set
 * @param format The project format (EDT or DESIGNER)
 * @param isDesignerSource Whether this is a Designer source set (converted from EDT)
 */
data class SourceSetContext(
    val sourceSet: SourceSet,
    val hashStorage: HashStorage,
    val format: ProjectFormat,
    val isDesignerSource: Boolean = false,
) {
    /**
     * Returns the base path for this source set
     */
    val basePath: java.nio.file.Path
        get() = sourceSet.basePath

    /**
     * Returns the name of this source set
     */
    val name: String
        get() = sourceSet.firstOrNull()?.name ?: "unknown"
}
