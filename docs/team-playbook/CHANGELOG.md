# Team Contract Changes

## 2026-10-23 - Infrastructure implementation (MSS-001/002/003/004/005/045)

Requested and authorized by Hà Minh Mẫn in this chat: implement the supplied infrastructure assignment and canonical team playbook. This checkout is already the server application root (the documented Assigment/); no nested application is created.

Affected owners: all server owners, for parent POM and common application/test configuration only. Canonical ports and service IDs follow the supplied playbook. Gateway adds /api routes; existing /identity, /catalog, /circulation, /reservation and /notification routes remain compatibility aliases. No domain API response, DTO, event topic or migration changes. No approval from other owners is asserted; future changes to their domain contracts require their approval.
