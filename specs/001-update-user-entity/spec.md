# Feature Specification: Update User Entity

**Feature Branch**: `001-update-user-entity`  
**Created**: 2026-01-29  
**Status**: Draft  
**Input**: User description: "@user-erd.png cập nhật entity User theo hình ảnh mới cập nhật"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Persist Extended User Information (Priority: P1)

The system must be able to store and retrieve detailed user information including personal details and status to support the new data model.

**Why this priority**: Core requirement to align the application with the new database schema design.

**Independent Test**: Verify that a User object can be instantiated and saved with all new fields populated.

**Acceptance Scenarios**:

1. **Given** a new user with full details (DOB, Gender, Phone, Branch, Status), **When** the user is saved to the database, **Then** all fields are persisted correctly.
2. **Given** an existing user, **When** the `status` is changed to `BANNED`, **Then** the status update is persisted.
3. **Given** a user with a phone number, **When** attempting to save another user with the same phone number, **Then** the system rejects the save (Unique constraint).

### Edge Cases

- What happens when `gender` or `status` is null? (Should be handled by validation or default values).
- What happens if `branch_id` does not refer to a valid branch? (Foreign key constraint behavior, though strictly logic-side for now if entity doesn't exist).
- What happens if `phone_number` exceeds 10 characters? (Database constraint).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The `User` entity MUST be updated to include `full_name` (varchar 50), replacing or mapping `displayName`.
- **FR-002**: The `User` entity MUST be updated to include `password_hash` (varchar 255), replacing or mapping `hashedPassword`.
- **FR-003**: The `User` entity MUST include a `date_of_birth` field (Date type).
- **FR-004**: The `User` entity MUST include an `avatar_url` field (String, max 255).
- **FR-005**: The `User` entity MUST include a `gender` field (supported by a new `Gender` Enum or similar).
- **FR-006**: The `User` entity MUST include a `status` field (supported by a new `UserStatus` Enum with values: ACTIVE, INACTIVE, BANNED, UNVERIFIED).
- **FR-007**: The `User` entity MUST include a `branch_id` field (Long/Integer) to store the reference key.
- **FR-008**: The `User` entity MUST include a `deleted_at` field (LocalDateTime) to support soft deletes.
- **FR-009**: The `User` entity MUST include a `phone_number` field (String, max 10) with a unique constraint.
- **FR-010**: Dependent classes (DTOs, Mappers) MUST be updated to accommodate the entity changes and ensure the project compiles.

### Key Entities

- **User**: The central entity representing a user. Updated attributes: `id`, `full_name`, `email`, `password_hash`, `date_of_birth`, `avatar_url`, `gender`, `created_at`, `updated_at`, `status`, `branch_id`, `deleted_at`, `phone_number`.
- **UserStatus (Enum)**: Defines the possible states of a user (ACTIVE, INACTIVE, BANNED, UNVERIFIED).
- **Gender (Enum)**: Defines gender options (e.g., MALE, FEMALE, OTHER).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The `User` class source code contains all fields defined in the ERD.
- **SC-002**: The application builds (`mvn clean compile` or equivalent) without errors.
- **SC-003**: Database schema generation produces a `users` table matching the ERD constraints (unique phone, enum values, field lengths).