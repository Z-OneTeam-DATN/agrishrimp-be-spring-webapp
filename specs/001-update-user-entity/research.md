# Research: Update User Entity

## Decisions

### 1. Database Schema & Entity
- **Decision**: Update `User` entity to map to `users` table with new columns.
- **Rationale**: Support new requirements for user profile management.
- **Details**:
  - `displayName` -> `full_name`
  - `hashedPassword` -> `password_hash`
  - New: `date_of_birth`, `avatar_url`, `gender`, `status`, `branch_id`, `deleted_at`, `phone_number`.
  - `branch_id` will be a simple `Long` as `Branch` entity does not exist yet.

### 2. Enums
- **Decision**: Create `com.zone.agri.entity.enums.Gender` and `com.zone.agri.entity.enums.UserStatus`.
- **Rationale**: Type-safe handling of gender and status.
- **Values**:
  - `Gender`: MALE, FEMALE, OTHER.
  - `UserStatus`: ACTIVE, INACTIVE, BANNED, UNVERIFIED.

### 3. API Contracts
- **Decision**: Update `UserInDto` and `UserOutDto` to include new fields.
- **Rationale**: Ensure API consumers can send/receive new data.

### 4. Storage for Avatars
- **Decision**: Use existing `S3Service` logic. The `User` entity only stores the URL string.
- **Rationale**: Minimal change, leverages existing infrastructure.

## Unknowns Resolved
- **Branch Entity**: Validated as non-existent. Will use `Long` for `branch_id`.
- **Gender Values**: Selected standard set (MALE, FEMALE, OTHER).
- **Status Values**: Confirmed from spec (ACTIVE, INACTIVE, BANNED, UNVERIFIED).
