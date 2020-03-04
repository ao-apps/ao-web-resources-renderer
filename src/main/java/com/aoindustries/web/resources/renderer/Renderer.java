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

import com.aoindustries.html.Html;
import com.aoindustries.html.Link;
import com.aoindustries.net.EmptyURIParameters;
import com.aoindustries.servlet.lastmodified.AddLastModified;
import com.aoindustries.servlet.lastmodified.LastModifiedUtil;
import com.aoindustries.web.resources.registry.Group;
import com.aoindustries.web.resources.registry.Registry;
import com.aoindustries.web.resources.registry.Style;
import com.aoindustries.web.resources.registry.Styles;
import com.aoindustries.web.resources.servlet.RegistryEE;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

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

	private static final Logger logger = Logger.getLogger(Renderer.class.getName());

	private static final String APPLICATION_ATTRIBUTE = Renderer.class.getName();

	/**
	 * Comments included when no styles written.
	 */
	private static final String
		NO_GROUPS            = "<!-- ao-web-resources-renderer: no groups -->",
		NO_STYLES            = "<!-- ao-web-resources-renderer: no styles -->",
		NO_APPLICABLE_STYLES = "<!-- ao-web-resources-renderer: no applicable styles -->";

	@WebListener
	public static class Initializer implements ServletContextListener {

		@Override
		public void contextInitialized(ServletContextEvent event) {
			get(event.getServletContext());
		}

		@Override
		public void contextDestroyed(ServletContextEvent event) {
			// Do nothing
		}
	}

	/**
	 * Gets the {@link Renderer web resource renderer} for the given {@linkplain ServletContext servlet context}.
	 */
	public static Renderer get(ServletContext servletContext) {
		Renderer renderer = (Renderer)servletContext.getAttribute(APPLICATION_ATTRIBUTE);
		if(renderer == null) {
			renderer = new Renderer(servletContext);
			servletContext.setAttribute(APPLICATION_ATTRIBUTE, renderer);
		}
		return renderer;
	}

	private final ServletContext servletContext;
	private Renderer(ServletContext servletContext) {
		this.servletContext = servletContext;
	}

	// TODO: Add/remove (or set/clear if only one) optimizer hooks: UnaryOperator<Set<...>>?

	/**
	 * Combines all the styles from {@link HttpServletRequest} and {@link HttpSession} into a single set,
	 * then renders the set of link tags.
	 *
	 * @param  indent  The indentation used between links, after a {@linkplain Html#nl() newline}.
	 */
	// TODO: Support included/inherited groups
	// TODO: Support minusGroups, which would suppress inherited/included groups
	public void renderStyles(
		HttpServletRequest request,
		HttpServletResponse response,
		Html html,
		Set<String> groups,
		String indent
	) throws IOException {
		if(logger.isLoggable(Level.FINER)) logger.finer("groups = " + groups);
		if(groups == null || groups.isEmpty()) {
			html.out.write(NO_GROUPS);
		} else {
			Registry requestRegistry = RegistryEE.get(servletContext, request);
			Registry sessionRegistry = null;
			HttpSession session = request.getSession(false);
			if(session != null) sessionRegistry = RegistryEE.get(session);
			// Perform a union of all selected groups from both request and session
			List<Styles> allStyles = new ArrayList<>();
			for(String groupName : groups) {
				Group group = requestRegistry.getGroup(groupName, false);
				if(logger.isLoggable(Level.FINER)) logger.finer("groupName: " + groupName + ", requestGroup: " + group);
				if(group != null) {
					allStyles.add(group.styles);
				}
				if(sessionRegistry != null) {
					group = sessionRegistry.getGroup(groupName, false);
					if(logger.isLoggable(Level.FINER)) logger.finer("groupName: " + groupName + ", sessionGroup: " + group);
					if(group != null) {
						allStyles.add(group.styles);
					}
				}
			}
			int size = allStyles.size();
			if(size == 0) {
				html.out.write(NO_STYLES);
			} else {
				Styles styles;
				if(size == 1) {
					styles = allStyles.get(0);
					if(logger.isLoggable(Level.FINER)) logger.finer("direct styles: " + styles.getSorted());
				} else {
					styles = Styles.union(allStyles);
					if(logger.isLoggable(Level.FINER)) logger.finer("unioned styles: " + styles.getSorted());
				}
				Set<Style> sorted = styles.getSorted();
				// TODO: Call optimizer hook
				boolean hasStyle = false;
				boolean didOne = false;
				Style.Direction responseDirection = null;
				for(Style style : sorted) {
					hasStyle = true;
					// Filter for direction
					boolean directionMatches;
					Style.Direction direction = style.getDirection();
					if(direction != null) {
						if(responseDirection == null) {
							responseDirection = Style.Direction.getDirection(response.getLocale());
						}
						directionMatches = (direction == responseDirection);
					} else {
						directionMatches = true;
					}
					if(directionMatches) {
						if(!didOne) {
							didOne = true;
						} else {
							html.nl();
							if(indent != null) html.out.write(indent);
						}
						@SuppressWarnings("deprecation")
						String ie = style.getIe();
						if(ie != null) {
							html.out.write("<!--[if ");
							html.out.write(ie);
							html.out.write('>');
						}
						// TODO: Support inline styles
						String href = style.getUri();
						html.link(Link.Rel.STYLESHEET)
							.href(href == null ? null :
								LastModifiedUtil.buildURL(
									servletContext,
									request,
									response,
									"/", // TODO: contextPath here to handle ../ breaking out of application? 
									     // TODO: All buildUrl add contextPath to servlet path to support ../ outside of application generally?
									     // TODO: / is prefixed with contextPath, so due to lack of normalization: /../ would effectively be relative to the current contextPath
									href,
									EmptyURIParameters.getInstance(),
									AddLastModified.AUTO,
									false,
									false
								)
							)
							.media(style.getMedia())
							.disabled(style.isDisabled())
							.__();
						if(ie != null) {
							html.out.write("<![endif]-->");
						}
					}
				}
				if(!didOne) {
					if(!hasStyle) {
						html.out.write(NO_STYLES);
					} else {
						html.out.write(NO_APPLICABLE_STYLES);
					}
				}
			}
		}
	}

	// TODO: renderScriptsHeadEnd
	// TODO: renderScriptsBodyEnd
}
