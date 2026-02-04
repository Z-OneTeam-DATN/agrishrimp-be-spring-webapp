# Data Model

## Entities

### User (`users`)

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | Long | Yes | Primary Key (Auto Inc) |
| `email` | String | Yes | Unique email address |
| `password_hash` | String | Yes | Hashed password (BCrypt) |
| `full_name` | String | Yes | User's full name (max 50) |
| `date_of_birth` | Date | No | User's birthday |
| `gender` | Enum | No | MALE, FEMALE, OTHER |
| `phone_number` | String | No | Unique phone number (max 10) |
| `avatar_url` | String | No | URL to avatar image |
| `status` | Enum | Yes | ACTIVE, INACTIVE, BANNED, UNVERIFIED |
| `branch_id` | Long | No | Reference to Branch ID |
| `created_at` | DateTime | Yes | Audit field |
| `updated_at` | DateTime | Yes | Audit field |
| `deleted_at` | DateTime | No | Soft delete timestamp |

### Enums

#### Gender
- `MALE`
- `FEMALE`
- `OTHER`

#### UserStatus
- `ACTIVE`
- `INACTIVE`
- `BANNED`
- `UNVERIFIED`

## API Models (DTOs)

### UserInDto (Request)
- `email`: String
- `password`: String
- `full_name`: String
- `date_of_birth`: Date
- `gender`: Gender
- `phone_number`: String
- `avatar_url`: String
- `status`: UserStatus
- `branch_id`: Long

### UserOutDto (Response)
- `id`: Long
- `email`: String
- `full_name`: String
- `date_of_birth`: Date
- `gender`: Gender
- `phone_number`: String
- `avatar_url`: String
- `status`: UserStatus
- `branch_id`: Long
- `created_at`: DateTime
- `updated_at`: DateTime
- `roles`: Set<RoleDto>
