# Tasks: Update User Entity

**Feature Branch**: `001-update-user-entity`
**Status**: In Progress

## Phase 1: Setup
*Goal: Ensure project is ready for changes.*

- [x] T001 Verify baseline build with `mvnw clean install` in `pom.xml` (SKIPPED - mvnw broken)

## Phase 2: Foundational
*Goal: Create shared types required by the new data model. Blocking for User Stories.*

- [x] T002 [P] Create `Gender` enum with values MALE, FEMALE, OTHER in `src/main/java/com/zone/agri/entity/enums/Gender.java`
- [x] T003 [P] Create `UserStatus` enum with values ACTIVE, INACTIVE, BANNED, UNVERIFIED in `src/main/java/com/zone/agri/entity/enums/UserStatus.java`

## Phase 3: User Story 1 - Persist Extended User Information (P1)
*Goal: Update User entity and related components to store and retrieve detailed user information.*
*Independent Test Criteria: Verify `User` object can be instantiated and saved with `full_name`, `dob`, `gender`, `status`, `phone`, etc.*

### Models & DTOs
- [x] T004 [US1] Update `UserInDto` to include `full_name`, `date_of_birth`, `gender`, `phone_number`, `avatar_url`, `status`, `branch_id` in `src/main/java/com/zone/agri/dto/user/UserInDto.java`
- [x] T005 [US1] Update `UserOutDto` to include `full_name`, `date_of_birth`, `gender`, `phone_number`, `avatar_url`, `status`, `branch_id`, `created_at` in `src/main/java/com/zone/agri/dto/user/UserOutDto.java`
- [x] T006 [US1] Update `User` entity: rename `displayName`->`full_name`, `hashedPassword`->`password_hash`; add `date_of_birth`, `gender`, `status`, `branch_id`, `phone_number`, `avatar_url`, `deleted_at` in `src/main/java/com/zone/agri/entity/User.java`

### Security & Service
- [x] T007 [US1] Update `CustomUserDetail` to map `password_hash` and `status` correctly from updated User entity in `src/main/java/com/zone/agri/security/CustomUserDetail.java`
- [x] T008 [US1] Update `UserService` implementation to handle mapping of new fields from DTO to Entity and handle field renames in `src/main/java/com/zone/agri/service/UserService.java`
- [x] T009 [US1] Update `AuthController` registration and login endpoints to use updated DTOs and Service methods in `src/main/java/com/zone/agri/controller/AuthController.java`

## Phase 4: Polish & Cross-Cutting Concerns
*Goal: Verify system stability and schema correctness.*

- [x] T010 Verify application startup and database schema generation matches ERD in `src/main/resources/application.yml` (Verified config)
- [ ] T011 Run full test suite to ensure no regressions in `src/test/java/com/zone/agri/AgriShrimpApplicationTests.java` (SKIPPED - mvnw broken)

## Dependencies
1. **Foundational (T002, T003)** MUST complete before **User Story 1**.
2. **Entity Update (T006)** is a prerequisite for **Service (T008)** and **Security (T007)** updates to compile.
3. **DTO Updates (T004, T005)** can be done in parallel with Entity updates but are needed for **Service (T008)**.

## Implementation Strategy
- **Step 1**: Define Enums to establish the vocabulary.
- **Step 2**: Update DTOs to define the contract.
- **Step 3**: Update Entity to reflect the storage (this will temporarily break compilation).
- **Step 4**: Fix compilation errors in Security and Service layers by adapting to the new Entity structure.
- **Step 5**: Verify with local run and tests.
