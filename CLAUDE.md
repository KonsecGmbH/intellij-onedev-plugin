# Onedev Plugin for Intellij IDEA

This program is a plugin for the Java IDE "IDEA" by Jetbrains. Idea has the notion of Tasks and Contexts which can be pulled
and updated from various kinds of servers and services, like github, gitlab, redmine etc.

This plugin provides access to the OneDev DevOps Platform, very often selfhosted. The plugin pulls and updates
tasks from and to an onedev server.

## Status

The plugin works and does what is needed. Client Certificates can be also configured to access the server.

## Plans

- Add support for "Builds" which are CI jobs in onedev. The plugin should show the running builds, maybe even see a running log of the build
- Notion of "main" projects and "forked" projects - issues are very often worked on on the main project, but commits are pushed to forked ones.
# Development Guidelines

### Philosophy
- **Always use the simplest solution, not the complicated clever one**
- Prefer editing existing files over creating new ones
- Ask before adding new dependencies
- Always check the files in llm/cache to see that is going on in development. Files ending in -PLAN.md constitute development plans
- if possible write tests for all functionality
- Don't try to be too clever
- ALWAYS save -PLAN files to llm/cache
- ALWAYS make sure that the tests run
- ALWAYS make sure the project compiles 
- Create tests for new features
- ALWAYS ask before adding a new dependency

### Testing Requirements
- Always run tests before big changes: `./gradlew test`
- Add tests when adding functionality
- Target 70% test coverage for domain code
- Avoid testing dependencies
- Keep tests fast with configurable short timeouts

### Code Standards
- Use Java 21 features appropriately
- Follow existing code patterns
- Security: Never expose/log secrets or keys
