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

package zone.gryphon.github.cache;

import lombok.NonNull;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTeam;
import zone.gryphon.github.utilities.IOUtilities;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class CachingOrganization {

    private final GHOrganization organization;

    private final Map<String, CachingTeam> teams = new HashMap<>();

    private final Map<String, GHRepository> repositories = new HashMap<>();

    public CachingOrganization(@NonNull GHOrganization organization) {
        this.organization = organization;
    }

    public String getLogin() {
        return IOUtilities.unwrap(organization::getLogin);
    }

    public CachingTeam getTeam(@NonNull String name) {
        return getRawTeams().get(name);
    }

    public GHRepository getRepository(@NonNull String name) {
        return getRawRepositories().get(name);
    }

    public Set<String> getRepositoryNames() {
        Set<String> set = getRawRepositories()
            .keySet()
            .stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(TreeSet::new));

        return Collections.unmodifiableSet(set);
    }

    private Map<String, CachingTeam> getRawTeams() {
        ensureTeamsAreLoaded();
        return teams;
    }

    private Map<String, GHRepository> getRawRepositories() {
        ensureRepositoriesAreLoaded();
        return repositories;
    }

    private void ensureRepositoriesAreLoaded() {

        if (!repositories.isEmpty()) {
            return;
        }

        for (GHRepository repository : IOUtilities.unwrap(organization::listRepositories)) {
            this.repositories.put(repository.getName(), repository);
        }

        // the organization has no repositories, add a sentinel value so we don't try to load them again
        if (repositories.isEmpty()) {
            repositories.put(null, null);
        }
    }

    private void ensureTeamsAreLoaded() {

        if (!teams.isEmpty()) {
            return;
        }

        for (GHTeam team : IOUtilities.unwrap(organization::listTeams)) {
            this.teams.put(team.getName(), new CachingTeam(team));
        }

        // the organization has no teams, add a sentinel value so we don't try to load them again
        if (teams.isEmpty()) {
            teams.put(null, null);
        }
    }
}
