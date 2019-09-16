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
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.GHTeam;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import zone.gryphon.github.utilities.IOUtilities;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class CachingUser {

    public static Optional<CachingUser> from(GitHub gitHub, String name) {
        try {
            // note: GitHub object caches users internally
            return Optional.ofNullable(gitHub.getUser(name)).map(CachingUser::new);
        } catch (FileNotFoundException e) {
            log.debug("User \"{}\" does not exist: {}: {}", name, e.getClass().getSimpleName(), e.getMessage());
            return Optional.empty();
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed to load user \"%s\"", name), e);
        }
    }


    @NonNull
    private final GHUser user;

    public GHUser getRawUser() {
        return user;
    }

    public String getName() {
        return IOUtilities.unwrap(user::getName);
    }

}
