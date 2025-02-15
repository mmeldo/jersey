/*
 * Copyright (c) 2013, 2021 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package org.glassfish.jersey.tools.plugins;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The plugins main MOJO class.
 * Walks through the maven dependency tree and creates the docbook output file.
 *
 * goal: generate
 * phase: process-sources
 * aggregator
 */
@Mojo(name = "generate", aggregator = true, defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class GenerateJerseyModuleListMojo extends AbstractMojo {

    /**
     * Placeholder in a table row template, will be replaced by the module name
     */
    static final String MODULE_NAME_PLACEHOLDER = "%NAME";

    /**
     * Placeholder in a table row template, will be replaced by the module description
     */
    static final String MODULE_DESCRIPTION_PLACEHOLDER = "%DESCRIPTION";

    /**
     * Placeholder in a header template, will be replaced by the category name.
     */
    static final String CATEGORY_CAPTION_PLACEHOLDER = "%CAPTION";

    /**
     * Placeholder in a header template, will be replaced by the groupId to create a unique element id.
     */
    static final String CATEGORY_GROUP_ID_PLACEHOLDER = "%GROUP_ID";

    /**
     * Placeholder in a template file, will be replaced by the generated table.
     */
    static final String CONTENT_PLACEHOLDER = "%CONTENT";

    /**
     * Placeholder in a table row template, will be replaced by the relative path to the link of project-info on java.net
     */
    static final String MODULE_LINK_PATH_PLACEHOLDER = "%LINK_PATH";

    /**
     * @parameter default-value="${project}
     * @required
     * @readonly
     */
    @Parameter(defaultValue = "${project.basedir}")
    private MavenProject mavenProject;

    /**
     * @component
     * @required
     * @readonly
     */
    @Parameter( defaultValue = "${session}", readonly = true )
    private MavenSession mavenSession;

    /**
     * @parameter default-value="modules.xml"
     */
    @Parameter(defaultValue = "modules.xml")
    private String outputFileName;

    /**
     * Name of a "template" file.
     * The file should contain all the static content from the docbook section related to modules.
     * The file should contain a placeholder {@see CONTENT_PLACEHOLDER}, which will be replaced by the generated table.
     *
     * @parameter
     */
    @Parameter
    private String templateFileName;

    /**
     * Template for a header part of each category.
     * Written to the output once per category.
     * Supported placeholders are {@see CATEGORY_CAPTION_PLACEHOLDER} and {@see CATEGORY_GROUP_ID_PLACEHOLDER}.
     *
     * @parameter
     */
    @Parameter
    private String tableHeaderFileName;

    /**
     * Template for a footer part of each category.
     * Written to the output once per category. No placeholders supported.
     *
     * @parameter
     */
    @Parameter
    private String tableFooterFileName;

    /**
     * Template for a table row in the module listing.
     * Written to the output once per module.
     * Supported placeholders are {@see MODULE_NAME_PLACEHOLDER} and {@see MODULE_DESCRIPTION_PLACEHOLDER}.
     *
     * @parameter
     */
    @Parameter
    private String tableRowFileName;

    /**
     * @parameter default-value="false"
     */
    @Parameter(defaultValue = "false")
    private boolean outputUnmatched;

    private Configuration configuration;

    private Log log;

    @Override
    public void execute() throws MojoExecutionException {

        try {
            configuration = prepareParameters();
        } catch (IOException e) {
            throw new MojoExecutionException("Plugin initialization failed. Problem reading input files.", e);
        }

        ProjectDependencyGraph graph = mavenSession.getProjectDependencyGraph();
        List<MavenProject> projects = graph.getDownstreamProjects(mavenProject, true);


        // categorize modules based on predefined categories
        Map<String, List<MavenProject>> categorizedProjects = categorizeModules(projects);

        // list of already "used" categories (maintained in order to identified unmatched modules later)
        Set<String> allGroups = new HashSet<>();

        // The entire module list table content - will replace the placeholder in the template
        StringBuilder content = new StringBuilder();

        // iterate over known categories
        for (PredefinedCategories category : PredefinedCategories.values()) {
            String groupId = category.getGroupId();

            allGroups.add(groupId);
            // ignore tests, but still keep them among the used categories (so that they do not appear in the unmatched list)
            if (groupId.contains("tests")) {
                continue;
            }
            List<MavenProject> projectsInCategory = new LinkedList<>();
            for (final Map.Entry<String, List<MavenProject>> entry : categorizedProjects.entrySet()) {
                final String key = entry.getKey();

                if (key.startsWith(groupId)) {
                    allGroups.add(key);
                    projectsInCategory.addAll(entry.getValue());
                }
            }

            content.append(processCategory(category, projectsInCategory));
        }

        // get the list of unmatched modules
        List<MavenProject> unmatched = new LinkedList<>();
        for (String groupId : categorizedProjects.keySet()) {
            if (!allGroups.contains(groupId)) {
                unmatched.addAll(categorizedProjects.get(groupId));
            }
        }

        if (unmatched.size() > 0) {
            log.warn("There are unmatched modules (" + unmatched.size() + ").");
            if (!outputUnmatched) {
                log.warn("You can configure the plugin to output unmatched modules by adding "
                        + "<outputUnmatched>true</outputUnmatched> in the configuration.");
            }
        }

        if (outputUnmatched) {
            content.append(processUnmatched(unmatched));
        }


        PrintWriter writer = null;
        try {
            writer = new PrintWriter(outputFileName);
            writer.println(configuration.getSectionTemplate().replace(CONTENT_PLACEHOLDER, content.toString()));
        } catch (FileNotFoundException e) {
            throw new MojoExecutionException("File not found exception");
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
        log.info("Output written to: " + outputFileName);
    }

    private Map<String, List<MavenProject>> categorizeModules(List<MavenProject> projects) {
        Map<String, List<MavenProject>> categorizedProjects = new HashMap<>();
        for (MavenProject project : projects) {
            String groupId = project.getGroupId();
            if (categorizedProjects.containsKey(groupId)) {
                categorizedProjects.get(groupId).add(project);
            } else {
                List<MavenProject> actualList = new LinkedList<>();
                actualList.add(project);
                categorizedProjects.put(groupId, actualList);
            }
        }
        return categorizedProjects;
    }

    private StringBuilder processUnmatched(List<MavenProject> unmatchedModules) {
        return processModuleList("Other", "other", unmatchedModules);
    }

    private StringBuilder processCategory(PredefinedCategories category, List<MavenProject> projectsInCategory) {
        return processModuleList(category.getCaption(), category.getGroupId(),
                projectsInCategory);
    }

    private StringBuilder processModuleList(String categoryCaption, String categoryId, List<MavenProject> projectsInCategory) {

        StringBuilder categoryContent = new StringBuilder();
        // Output table header for the category
        categoryContent.append(configuration.getTableHeader()
                .replace(CATEGORY_CAPTION_PLACEHOLDER, categoryCaption)
                .replace(CATEGORY_GROUP_ID_PLACEHOLDER, categoryId));

        // Sort projects in each category alphabetically
        Collections.sort(projectsInCategory, new Comparator<MavenProject>() {
            @Override
            public int compare(MavenProject o1, MavenProject o2) {
                return o1.getArtifactId().compareTo(o2.getArtifactId());
            }
        });

        // Output projects in a category
        for (MavenProject project : projectsInCategory) {
            // skip the "parent" type projects
            if (project.getArtifactId().equals("project")) {
                continue;
            }


            String linkPrefix = getLinkPath(project);

            categoryContent.append(configuration.getTableRow()
                    .replace(MODULE_NAME_PLACEHOLDER, project.getArtifactId())
                    .replace(MODULE_DESCRIPTION_PLACEHOLDER, project.getDescription())
                    .replace(MODULE_LINK_PATH_PLACEHOLDER, linkPrefix + project.getArtifactId()));
        }

        categoryContent.append(configuration.getTableFooter());
        return categoryContent;
    }

    /**
     * Build the project-info link path by including all the artifactId up to (excluding) the root parent
     * @param project project for which the path should be determined.
     * @return path consisting of hierarchically nested maven artifact IDs. Used for referencing to the project-info on java.net
     */
    private String getLinkPath(MavenProject project) {
        String path = "";
        MavenProject parent = project.getParent();
        while (parent != null
                && !(parent.getArtifactId().equals("project") && parent.getGroupId().equals("org.glassfish.jersey"))) {
            path = parent.getArtifactId() + "/" + path;
            parent = parent.getParent();
        }
        return path;
    }

    @Override
    public void setLog(org.apache.maven.plugin.logging.Log log) {
        this.log = log;
    }

    public String readFile(String fileName) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(fileName));
        String s;
        StringBuilder sb = new StringBuilder();
        while ((s = reader.readLine()) != null) {
            sb.append(s); sb.append("\n");
        }
        reader.close();
        return sb.toString();
    }

    public Configuration prepareParameters() throws IOException {
        Configuration configuration = new Configuration();
        configuration.setSectionTemplate(readFile(templateFileName));
        configuration.setTableHeader(readFile(tableHeaderFileName));
        configuration.setTableRow(readFile(tableRowFileName));
        configuration.setTableFooter(readFile(tableFooterFileName));
        return configuration;
    }

}
