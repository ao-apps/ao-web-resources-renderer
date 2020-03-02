/*
 * ao-web-resources-renderer - Renders HTML for web resource management.
 * Copyright (C) 2020  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of ao-web-resources-renderer.
 *
 * ao-web-resources-renderer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ao-web-resources-renderer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ao-web-resources-renderer.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.web.resources.renderer;

import com.aoindustries.web.resources.servlet.RegistryEE;

/**
 * Renders the HTML output for web resource management.
 * <p>
 * This provides a basic implementation without optimization.
 * However, it provides a hook for optimizers to affect what it rendered.
 * </p>
 * <p>
 * This is placed in a distinct project from {@link RegistryEE} because it
 * adds several dependencies that are not required by projects that simply
 * register themselves.  This choice is consistent with our "micro project"
 * principle.
 * </p>
 */
public class Renderer {

	// TODO

	private Renderer() {}
}
