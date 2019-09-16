# github-permission-automation [![Build status][build-icon]][build-link] [![Maven Central][mvn-central-icon]][mvn-central-link]

[build-link]: https://jenkins.gryphon.zone/job/gryphon-zone/job/github-permission-automation/job/master/
[build-icon]: https://jenkins.gryphon.zone/buildStatus/icon?job=gryphon-zone%2Fgithub-permission-automation%2Fmaster

[mvn-central-icon]: https://maven-badges.herokuapp.com/maven-central/zone.gryphon.github/github-permission-automation/badge.png
[mvn-central-link]: https://search.maven.org/artifact/zone.gryphon.github/github-permission-automation/

[github-releases]: https://github.com/gryphon-zone/github-permission-automation/releases/latest


## Configuration File

Permission configuration is done via a YAML file.
The format of this file is given below:

```yaml
# Map of organization name -> organization configuration.
# The keys in the map are the literal organization names, and the values are the configuration for that organization.
organizations:

  # Configuration for the organization named `example-organization`.
  example-organization:

    # Map of team name -> team configuration.
    # The keys in the map are the literal team names, and the values are the configuration for that team.
    teams:

      # Configuration for the team named `example-team`.
      example-team:
        
        # Default permission this team should have over the repositories.
        # Possible values:
        #    READ  - read only access to the repository
        #    WRITE - write (push) access to the repository
        #    ADMIN - administrative access to the repository
        #    NONE  - the team will have no access to the repository
        #
        # default: NONE
        permission: NONE

        # List of repositories to apply permissions to.
        # If the value is null (or an empty list), the permission will be applied to all repositories in the organization.
        #
        # default: null
        repositories:
          - 'repository-one'
          - 'repository-two'
        
        # List of repositories to exclude from processing.
        # Permissions for any repositories in this list will not be modified, regardless of the existing value.
        #
        # This list makes the most sense to use when `repositories` is left as null, 
        # to prevent unintentional permission modifications to restricted repositories.
        #
        # default: null
        exclusions:
          - 'repository-three'
          - 'repository-four'
        
        # Map of repository -> permission, which will override the default permission.
        # Note that if a repository appears both in the `overrides` and `exclusions`, `exclusions` takes precedence.
        #
        # default: null
        overrides:
          repository-five:  READ
          repository-six:   WRITE
          repository-seven: ADMIN
          repository-eight: NONE

        # Configuration for membership on the team.
        #
        # default: null
        membership:
          
          # List of Github users who should be added to the team with the `MEMBER` role.
          # These users will not be able to edit the team, but will benefit from the access it gives.
          #
          # default: null
          members:
            - 'one'
            - 'two'
          
          # List of Github users who should be added to the team with the `MAINTAINER` role.
          # These users can edit team membership, in addition to benefiting from the access the team grants.
          #
          # default: null
          admins:
            - 'three'
            - 'four'
          
          # List of Github users who should not be on the team.
          # If any of these users are added to the team, they will be removed automatically.
          #
          # default: null
          banned:
            - 'none'
```

As an example, here's what a potential configuration might look like:
```yaml
organizations:
  my-organization:
    teams:
      administrators:
        members:
          members:
            - 'user1'
            - 'user2'
          admins:
            - 'user3'
        permission: ADMIN
        exclusions:
          - 'secret-repo'
        overrides:
          read-only-repo: READ
```
