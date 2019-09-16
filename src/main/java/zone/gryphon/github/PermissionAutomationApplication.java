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

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHTeam;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import zone.gryphon.github.cache.CachingOrganization;
import zone.gryphon.github.cache.CachingTeam;
import zone.gryphon.github.cache.CachingUser;
import zone.gryphon.github.configuration.Configuration;
import zone.gryphon.github.configuration.OrganizationConfiguration;
import zone.gryphon.github.configuration.TeamConfiguration;
import zone.gryphon.github.configuration.TeamMembershipConfiguration;
import zone.gryphon.github.model.RepositoryPermission;
import zone.gryphon.github.utilities.CollectionUtilities;
import zone.gryphon.github.utilities.FileConverter;
import zone.gryphon.github.utilities.FileExistsValidator;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static zone.gryphon.github.model.RepositoryPermission.NONE;

@Slf4j
public class PermissionAutomationApplication {

    public static void main(String... args) throws Exception {
        new PermissionAutomationApplication(args).run();
    }

    @Parameter(
        names = "--github",
        arity = 1,
        description = "URL of the Github API to use"
    )
    private String url = "https://api.github.com";

    @Parameter(
        names = {"-u", "--user"},
        arity = 1,
        description = "Username to connect with. Not required when using token authentication."
    )
    private String user = null;

    @Parameter(
        names = {"-p", "--password"},
        arity = 1,
        description = "" +
            "Password to connect with\n" +
            "Only one of password/token needs to be specified, if both are provided the token will be used.",
        password = true
    )
    private String password = null;

    @Parameter(
        names = {"-t", "--token"},
        arity = 1,
        description = "" +
            "Token to connect with\n" +
            "Only one of password/token needs to be specified, if both are provided the token will be used.",
        password = true
    )
    private String token = null;

    @Parameter(
        names = {"-f", "--file"},
        converter = FileConverter.class,
        validateValueWith = FileExistsValidator.class,
        arity = 1,
        description = "" +
            "Configuration file to use.",
        password = true
    )
    private File file = new File("github.yaml");

    @Parameter(
        names = {"-h", "--help"},
        help = true,
        description = "Print this help message and exit"
    )
    private boolean printHelp = false;

    private final Configuration configuration;

    private final GitHub github;

    private PermissionAutomationApplication(String... args) throws Exception {

        try {
            JCommander.newBuilder()
                .addObject(this)
                .acceptUnknownOptions(false)
                .build()
                .parse(args);
        } catch (ParameterException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            log.error("Failed to parse arguments", e);
            System.exit(1);
        }

        if (printHelp) {
            JCommander.newBuilder()
                .addObject(this)
                .build()
                .usage();
            System.exit(0);
        }

        try {
            this.configuration = readConfiguration();
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            throw new RuntimeException("This can never be reached");
        }

        this.github = connect();
    }

    private Configuration readConfiguration() throws IOException {
        Configuration configuration = new YAMLMapper()
            .disable(FAIL_ON_UNKNOWN_PROPERTIES)
            .readValue(file, Configuration.class);

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Set<ConstraintViolation<Configuration>> violations = factory.getValidator().validate(configuration);

            if (violations.isEmpty()) {
                return configuration;
            }

            StringBuilder builder = new StringBuilder();

            builder
                .append("Configuration file \"")
                .append(file.getAbsolutePath())
                .append("\" is invalid.\n")
                .append(violations.size())
                .append(" field")
                .append(violations.size() == 1 ? "" : "s")
                .append(" with validation failures:\n");

            for (ConstraintViolation<Configuration> violation : violations) {
                builder.append("  field \"").append(violation.getPropertyPath()).append("\": ").append(violation.getMessage()).append("\n");
            }

            throw new IllegalArgumentException(builder.toString());
        }
    }

    private GitHub connect() throws IOException {
        GitHubBuilder builder = new GitHubBuilder();

        builder.withEndpoint(url);

        if (!Strings.isNullOrEmpty(token)) {

            if (!Strings.isNullOrEmpty(user)) {
                // use token authentication with a user; user should match what's on the token
                builder.withOAuthToken(token, user);
            } else {
                // use token authentication without a user; user will be determined from token
                builder.withOAuthToken(token);
            }

        } else if (!Strings.isNullOrEmpty(password)) {

            if (!Strings.isNullOrEmpty(user)) {
                // use password authentication
                builder.withPassword(user, password);
            } else {
                throw new IllegalArgumentException("Cannot supply password without username");
            }

        }

        return builder.build();
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
        CachingOrganization organization = new CachingOrganization(github.getOrganization(organizationName));

        CachingTeam team = organization.getTeam(teamName);

        if (team == null) {
            log.error("Team \"{}\" under organization \"{}\" does not exist, skipping", teamName, organizationName);
            return;
        }

        if (teamConfiguration.getMembership() != null) {
            configureTeamMembership(organization, team, teamConfiguration.getMembership());
        } else {
            log.warn("No membership configuration for team \"{}\"", teamName);
        }

        configureRepositoryPermissions(organization, teamName, teamConfiguration);
    }

    private void configureTeamMembership(CachingOrganization organization, CachingTeam team, TeamMembershipConfiguration membership) {
        Set<String> members = CollectionUtilities.nullToEmpty(membership.getMembers());
        Set<String> admins = CollectionUtilities.nullToEmpty(membership.getAdmins());
        Set<String> banned = CollectionUtilities.nullToEmpty(membership.getBanned());

        configureMembership(organization, team, members, GHTeam.Role.MEMBER);
        configureMembership(organization, team, admins, GHTeam.Role.MAINTAINER);
        configureMembership(organization, team, banned, null);
    }

    private void configureMembership(CachingOrganization organization, CachingTeam team, Set<String> members, GHTeam.Role role) {
        for (String member : members) {
            Optional<CachingUser> maybeUser = CachingUser.from(github, member);

            if (!maybeUser.isPresent()) {
                log.warn("User \"{}\" does not exist, skipping", member);
                continue;
            }

            CachingUser user = maybeUser.get();

            final String NONE = "NONE";

            int padding = Math.max(NONE.length(), Arrays.stream(GHTeam.Role.values())
                .map(Enum::name)
                .mapToInt(String::length)
                .max()
                .orElse(0));

            // pad the logging so that it all lines up
            String width = String.format("%%-%ds", padding);

            String roleName = role == null ? NONE : role.name();

            log.info("Setting access to team \"{}\" in organization \"{}\" as {} for user \"{}\" ({})",
                team.getName(), organization.getLogin(), String.format(width, roleName), member, user.getName());

            if (role == null) {
                team.remove(user.getRawUser());
            } else {
                team.add(user.getRawUser(), role);
            }
        }
    }

    private void configureRepositoryPermissions(CachingOrganization organization, String teamName, TeamConfiguration teamConfiguration) {
        Map<String, RepositoryPermission> repositoryPermissions = calculateRepositoryPermissionsFor(organization, teamConfiguration);

        CachingTeam team = organization.getTeam(teamName);

        int padding = Arrays.stream(RepositoryPermission.values())
            .map(Enum::name)
            .map(String::length)
            .mapToInt(Integer::intValue)
            .max()
            .orElse(0);

        // pad the logging so that it all lines up
        String width = String.format("%%-%ds", padding);

        for (Map.Entry<String, RepositoryPermission> tuple : repositoryPermissions.entrySet()) {
            String repositoryName = tuple.getKey();
            RepositoryPermission permission = tuple.getValue();

            log.info("Setting access role as {} for team \"{}\" over \"{}/{}\"", String.format(width, permission), teamName, organization.getLogin(), repositoryName);
            if (Objects.equals(NONE, permission)) {
                team.remove(organization.getRepository(repositoryName));
            } else {
                team.add(organization.getRepository(repositoryName), map(permission));
            }
        }
    }

    private GHOrganization.Permission map(RepositoryPermission permission) {
        switch (permission) {
            case WRITE:
                return GHOrganization.Permission.PUSH;
            case READ:
                return GHOrganization.Permission.PULL;
            case ADMIN:
                return GHOrganization.Permission.ADMIN;
            case NONE:
            default:
                throw new IllegalArgumentException("Cannot map permission \"" + permission + "\"");
        }
    }

    private Map<String, RepositoryPermission> calculateRepositoryPermissionsFor(CachingOrganization organization, TeamConfiguration team) {
        final Set<String> available = organization.getRepositoryNames();

        final RepositoryPermission defaultPermission = Optional.ofNullable(team.getPermission()).orElse(NONE);
        final Set<String> exclusions = CollectionUtilities.nullToEmpty(team.getExclusions());
        final Set<String> requestedRepos = CollectionUtilities.nullToEmpty(team.getRepositories());
        final Map<String, RepositoryPermission> overrides = CollectionUtilities.nullToEmpty(team.getOverrides());

        final Set<String> requested = new TreeSet<>(CollectionUtilities.firstNonEmpty(requestedRepos, available));

        if (requested.isEmpty()) {
            return Collections.emptyMap();
        }

        final Map<String, RepositoryPermission> out = new HashMap<>();

        for (String repository : requested) {

            if (exclusions.contains(repository)) {
                log.debug("Repository \"{}\" is excluded, skipping", repository);
                continue;
            }

            if (!available.contains(repository)) {
                log.warn("Requested repository \"{}/{}\" does not exist, ignoring", organization.getLogin(), repository);
                continue;
            }

            out.put(repository, overrides.getOrDefault(repository, defaultPermission));
        }

        return out;
    }


}
