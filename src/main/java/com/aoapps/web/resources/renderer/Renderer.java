/*
 * ao-web-resources-renderer - Renders HTML for web resource management.
 * Copyright (C) 2020, 2021, 2022  AO Industries, Inc.
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
 * along with ao-web-resources-renderer.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.aoapps.web.resources.renderer;

import com.aoapps.html.any.AnyLINK;
import com.aoapps.html.any.AnyUnion_Metadata_Phrasing;
import com.aoapps.net.EmptyURIParameters;
import com.aoapps.servlet.attribute.ScopeEE;
import com.aoapps.servlet.lastmodified.AddLastModified;
import com.aoapps.servlet.lastmodified.LastModifiedUtil;
import com.aoapps.web.resources.registry.Group;
import com.aoapps.web.resources.registry.Registry;
import com.aoapps.web.resources.registry.Style;
import com.aoapps.web.resources.registry.Styles;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
 * This is placed in a distinct project from {@link com.aoapps.web.resources.servlet.RegistryEE} because it
 * adds several dependencies that are not required by projects that simply
 * register themselves.  This choice is consistent with our "micro project"
 * principle.
 * </p>
 */
public class Renderer {

  private static final Logger logger = Logger.getLogger(Renderer.class.getName());

  private static final ScopeEE.Application.Attribute<Renderer> APPLICATION_ATTRIBUTE =
      ScopeEE.APPLICATION.attribute(Renderer.class.getName());

  /**
   * Comments included when no styles written.
   */
  private static final String
      NO_REGISTRIES        = "<!-- ao-web-resources-renderer: no registries -->",
      NO_ACTIVATIONS       = "<!-- ao-web-resources-renderer: no activations -->",
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
    return APPLICATION_ATTRIBUTE.context(servletContext).computeIfAbsent(__ -> new Renderer(servletContext));
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
   * @param  registeredActivations  Should the registered activations be applied?
   *
   * @param  activations  Additional activations applied after those configured in the registries.
   *
   * @param  registries  Iterated up to twice: first to determine group activations,
   *                     then to union the styles from all activated groups.
   */
  // TODO: Support included/inherited groups
  public void renderStyles(
      HttpServletRequest request,
      HttpServletResponse response,
      AnyUnion_Metadata_Phrasing<?, ?> content,
      boolean registeredActivations,
      Map<Group.Name, Boolean> activations,
      Iterable<Registry> registries
  ) throws IOException {
    if (logger.isLoggable(Level.FINER)) {
      logger.finer("registries = " + registries);
    }
    if (registries == null) {
      if (logger.isLoggable(Level.FINER)) {
        logger.finer(NO_REGISTRIES);
      }
      content.unsafe(NO_REGISTRIES); // TODO: comment method
    } else {
      // Resolve current activations
      Set<Group.Name> groups = new HashSet<>();
      if (registeredActivations) {
        boolean hasRegistry = false;
        for (Registry registry : registries) {
          if (registry != null) {
            hasRegistry = true;
            for (Map.Entry<Group.Name, Boolean> entry : registry.getActivations().entrySet()) {
              Group.Name name = entry.getKey();
              assert entry.getValue() != null : "null activations are removed, not set as an entry";
              boolean activated = entry.getValue();
              if (activated) {
                groups.add(name);
              } else {
                groups.remove(name);
              }
            }
          }
        }
        if (!hasRegistry) {
          if (logger.isLoggable(Level.FINER)) {
            logger.finer(NO_REGISTRIES);
          }
          content.unsafe(NO_REGISTRIES); // TODO: comment method
          return;
        }
      }
      if (activations != null) {
        for (Map.Entry<Group.Name, Boolean> entry : activations.entrySet()) {
          Group.Name name = entry.getKey();
          Boolean activated = entry.getValue();
          if (activated != null) {
            if (activated) {
              groups.add(name);
            } else {
              groups.remove(name);
            }
          }
        }
      }
      if (logger.isLoggable(Level.FINER)) {
        logger.finer("groups = " + groups);
      }
      if (groups.isEmpty()) {
        if (logger.isLoggable(Level.FINER)) {
          logger.finer(NO_ACTIVATIONS);
        }
        content.unsafe(NO_ACTIVATIONS); // TODO: comment method
        return;
      }
      // Find all the styles for all the activated groups in all registries
      List<Styles> allStyles = new ArrayList<>();
      boolean hasRegistry = false;
      for (Registry registry : registries) {
        if (registry != null) {
          hasRegistry = true;
          for (Group.Name name : groups) {
            Group group = registry.getGroup(name, false);
            if (logger.isLoggable(Level.FINEST)) {
              logger.finest("name: " + name + ", group: " + group);
            }
            if (group != null) {
              allStyles.add(group.styles);
            }
          }
        }
      }
      if (!hasRegistry) {
        if (logger.isLoggable(Level.FINER)) {
          logger.finer(NO_REGISTRIES);
        }
        content.unsafe(NO_REGISTRIES); // TODO: comment method
        return;
      }
      // Perform a union of all styles
      int size = allStyles.size();
      if (size == 0) {
        if (logger.isLoggable(Level.FINER)) {
          logger.finer(NO_STYLES);
        }
        content.unsafe(NO_STYLES); // TODO: comment method
      } else {
        Styles styles;
        if (size == 1) {
          styles = allStyles.get(0);
          if (logger.isLoggable(Level.FINEST)) {
            logger.finest("direct styles: " + styles);
          }
        } else {
          styles = Styles.union(allStyles);
          if (logger.isLoggable(Level.FINEST)) {
            logger.finest("unioned styles: " + styles);
          }
        }
        Set<Style> sorted = styles.getSorted();
        if (logger.isLoggable(Level.FINER)) {
          logger.finer("sorted: " + styles);
        }
        // TODO: Call optimizer hook
        boolean hasStyle = false;
        boolean didOne = false;
        Style.Direction responseDirection = null;
        for (Style style : sorted) {
          hasStyle = true;
          // Filter for direction
          boolean directionMatches;
          Style.Direction direction = style.getDirection();
          if (direction != null) {
            if (responseDirection == null) {
              responseDirection = Style.Direction.getDirection(response.getLocale());
            }
            directionMatches = (direction == responseDirection);
          } else {
            directionMatches = true;
          }
          if (directionMatches) {
            didOne = true;
            // TODO: Support inline styles
            String href = style.getUri();
            content.link(AnyLINK.Rel.STYLESHEET)
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
          }
        }
        if (!didOne) {
          if (!hasStyle) {
            if (logger.isLoggable(Level.FINER)) {
              logger.finer(NO_STYLES);
            }
            content.unsafe(NO_STYLES); // TODO: comment method
          } else {
            if (logger.isLoggable(Level.FINER)) {
              logger.finer(NO_APPLICABLE_STYLES);
            }
            content.unsafe(NO_APPLICABLE_STYLES); // TODO: comment method
          }
        }
      }
    }
  }

  /**
   * @see  #renderStyles(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, com.aoapps.html.any.AnyUnion_Metadata_Phrasing, boolean, java.util.Map, java.lang.Iterable)
   */
  public void renderStyles(
      HttpServletRequest request,
      HttpServletResponse response,
      AnyUnion_Metadata_Phrasing<?, ?> content,
      boolean registeredActivations,
      Map<Group.Name, Boolean> activations,
      Registry ... registries
  ) throws IOException {
    renderStyles(
        request,
        response,
        content,
        registeredActivations,
        activations,
        (registries == null) ? null : Arrays.asList(registries)
    );
  }

  // TODO: renderScriptsHeadEnd
  // TODO: renderScriptsBodyEnd
}
