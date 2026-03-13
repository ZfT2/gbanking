# Contributing to GBanking

Thank you for your interest in contributing to **GBanking**!

Contributions are welcome. This document describes how to contribute code,
report issues and propose improvements.

---

# Project Status

⚠ **Alpha Software**

GBanking is currently in **alpha stage**.

The application is **not yet complete and not suitable for production use**.
Breaking changes may occur at any time.

Please avoid using real bank credentials during testing.

---

# How to Contribute

You can contribute in several ways:

- reporting bugs
- suggesting new features
- improving documentation
- submitting pull requests

---

# Reporting Issues

Before opening a new issue please check:

- if the issue already exists
- if it has already been fixed in the current development version

When reporting a bug please include:

- operating system
- Java version
- steps to reproduce the problem
- relevant log output

---

# Development Setup

## Requirements

To build the project locally you need:

- **Java 21**
- **Maven**
- Git

Verify your Java version:

```
java -version
```

---

## Clone the Repository

```
git clone https://github.com/ZfT2/gbanking.git
cd gbanking
```

---

## Build the Project

```
mvn clean install
```

---

## Build Platform Distributions

Windows:

```
mvn -Pwindows clean package
```

Linux:

```
mvn -Plinux clean package
```

macOS:

```
mvn -Pmac clean package
```

Distribution archives will be created in:

```
target/
```

---

# Code Style

Please follow these guidelines:

- Follow standard Java naming conventions
- Keep methods short and readable
- Ask before if you want to introduce new dependencies

---

# Logging

The project uses **Log4j2**.

Prefer structured logging instead of `System.out.println`.

Example:

```java
log.info("Loading account {}", accountId);
```

---

# Commit Guidelines

Write clear commit messages.

Example:

```
Add transaction import feature
```

or

```
Fix account list refresh bug
```

Good commits:

- describe **what changed**
- optionally explain **why**

---

# Pull Requests

Steps for contributing code:

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push the branch
5. Open a Pull Request

Example:

```
git checkout -b feature/improve-transaction-import
```

---

# Code Review

Pull requests may receive review feedback.

Please:

- respond to comments
- update your branch if necessary
- keep discussions constructive

---

# License

By contributing to this project you agree that your contributions
will be licensed under the **GNU General Public License v3.0**.

See:

```
LICENSE
```

---

# Thank You

Your contributions help improve the project.
Thank you for your support!