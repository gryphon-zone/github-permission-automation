/*
 * Copyright 2019-2019 Gryphon Zone
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zone.gryphon.github;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHOrganization.Permission;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTeam;
import org.kohsuke.github.GitHub;
import zone.gryphon.github.configuration.Configuration;
import zone.gryphon.github.configuration.OrganizationConfiguration;
import zone.gryphon.github.configuration.TeamConfiguration;
import zone.gryphon.github.utilities.ProxyIterator;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Slf4j
public class PermissionAutomationApplication {

    public static void main(String... args) throws Exception {
        new PermissionAutomationApplication(args).run();
    }

    private final Configuration configuration;

    private final GitHub github;

    private PermissionAutomationApplication(String... args) throws Exception {
        this.configuration = new YAMLMapper().readValue(new File("src/main/resources/configuration.yaml"), Configuration.class);
        this.github = null;
    }

    public void run() throws IOException {
        for (Map.Entry<String, OrganizationConfiguration> tuple : configuration.getOrganizations().entrySet()) {
            process(tuple.getKey(), tuple.getValue());
        }
    }

    private void process(String organization, OrganizationConfiguration configuration) throws IOException {
        for (Map.Entry<String, TeamConfiguration> tuple : configuration.getTeams().entrySet()) {
            process(organization, tuple.getKey(), tuple.getValue());
        }
    }

    private void process(String organizationName, String teamName, TeamConfiguration teamConfiguration) throws IOException {
        GHOrganization org = github.getOrganization(organizationName);

        // TODO replace this call, under the covers it loads all teams
        GHTeam team = org.getTeamByName(teamName);

        Map<String, Permission> repositoryPermissions = calculateRepositoryPermissionsFor(org, teamConfiguration);

        for (Map.Entry<String, Permission> tuple : repositoryPermissions.entrySet()) {
            String repository = tuple.getKey();
            Permission permission = tuple.getValue();

        }


        log.info("{} - {} - {}", organizationName, teamName, teamConfiguration);
    }

    private Map<String, Permission> calculateRepositoryPermissionsFor(GHOrganization org, TeamConfiguration team) throws IOException {
        final Set<String> available = getAllRepositoryNames(org);
        final Permission defaultPermission = Optional.ofNullable(team.getPermission()).orElse(Permission.PULL);

        final Set<String> exclusions = nullToEmpty(team.getExclusions());
        final Set<String> requestedRepos = nullToEmpty(team.getRepositories());
        final Map<String, Permission> overrides = nullToEmpty(team.getOverrides());

        final Set<String> requested = new TreeSet<>(firstNonEmpty(available, requestedRepos));

        if (requested.isEmpty()) {
            return Collections.emptyMap();
        }

        final Map<String, Permission> out = new HashMap<>();

        for (String repository : requested) {

            if (exclusions.contains(repository)) {
                log.debug("Repository \"{}\" is excluded, skipping", repository);
                continue;
            }

            if (!available.contains(repository)) {
                log.warn("Requested repository \"{}/{}\" does not exist, ignoring", org.getName(), repository);
                continue;
            }

            out.put(repository, overrides.getOrDefault(repository, defaultPermission));
        }


        return out;
    }

    private Set<String> getAllRepositoryNames(GHOrganization org) {
        Set<String> available = new HashSet<>();
        new ProxyIterator<>(org.listRepositories().iterator(), GHRepository::getName).forEachRemaining(available::add);
        return available;
    }

    private <T> Set<T> nullToEmpty(Set<T> input) {
        return input == null ? Collections.emptySet() : input;
    }

    private <K, V> Map<K, V> nullToEmpty(Map<K, V> input) {
        return input == null ? Collections.emptyMap() : input;
    }

    private <T> Set<T> firstNonEmpty(Set... collection) {

        //noinspection unchecked
        for (Set<T> candidate : collection) {
            if (candidate != null && !candidate.isEmpty()) {
                return candidate;
            }
        }

        return Collections.emptySet();
    }


}
