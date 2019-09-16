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
import lombok.RequiredArgsConstructor;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTeam;
import org.kohsuke.github.GHUser;
import zone.gryphon.github.utilities.IOUtilities;

@RequiredArgsConstructor
public class CachingTeam {

    @NonNull
    private final GHTeam team;

    public String getName() {
        return IOUtilities.unwrap(team::getName);
    }

    public void add(GHRepository r, GHOrganization.Permission permission) {
        IOUtilities.unwrap(() -> {
            team.add(r, permission);
            return null;
        });
    }

    public void remove(GHRepository repo) {
        IOUtilities.unwrap(() -> {
            team.remove(repo);
            return null;
        });
    }

    public void remove(GHUser user) {
        IOUtilities.unwrap(() -> {
            team.remove(user);
            return null;
        });
    }

    public void add(GHUser user, GHTeam.Role role) {
        IOUtilities.unwrap(() -> {
            team.add(user, role);
            return null;
        });
    }

}
