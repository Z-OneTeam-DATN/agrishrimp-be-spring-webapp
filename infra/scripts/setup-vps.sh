#!/usr/bin/env bash
# =============================================================
# setup-vps.sh — Chạy MỘT LẦN khi setup VPS mới
# Mục đích: cài Docker, tạo thư mục, tạo SSH key, cấu hình Nginx
# Chạy với: sudo bash setup-vps.sh
# =============================================================
set -euo pipefail

DEPLOY_DIR="/home/bnhien40/agrishrimp"
NGINX_CONF="/etc/nginx/sites-available/agrishrimp"

echo "======================================"
echo "  AgriShrimp VPS Setup Script"
echo "======================================"

# ── 1. Cài Docker ─────────────────────────────────────────────
if ! command -v docker &>/dev/null; then
  echo "[1/7] Installing Docker..."
  curl -fsSL https://get.docker.com | sh
  systemctl enable docker
  systemctl start docker
  # Thêm user hiện tại vào group docker (cần re-login)
  usermod -aG docker "$SUDO_USER"
  echo "✅ Docker installed. Re-login required for group change."
else
  echo "[1/7] Docker already installed: $(docker --version)"
fi

# ── 2. Cài Docker Compose plugin ──────────────────────────────
if ! docker compose version &>/dev/null; then
  echo "[2/7] Installing Docker Compose plugin..."
  apt-get install -y docker-compose-plugin
else
  echo "[2/7] Docker Compose already installed: $(docker compose version)"
fi

# ── 3. Tạo thư mục deploy ─────────────────────────────────────
echo "[3/7] Creating deploy directory: $DEPLOY_DIR"
mkdir -p "$DEPLOY_DIR"
chmod 750 "$DEPLOY_DIR"

# ── 4. Copy docker-compose và tạo .env từ template ────────────
echo "[4/7] Copying compose file..."
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cp "$SCRIPT_DIR/../docker-compose.prod.yml" "$DEPLOY_DIR/docker-compose.prod.yml"

if [ ! -f "$DEPLOY_DIR/.env" ]; then
  cp "$SCRIPT_DIR/../.env.template" "$DEPLOY_DIR/.env"
  chmod 600 "$DEPLOY_DIR/.env"
  echo ""
  echo "⚠️  QUAN TRỌNG: Điền secrets vào file sau trước khi deploy:"
  echo "    $DEPLOY_DIR/.env"
  echo ""
else
  echo "    .env already exists — skipped"
fi

# ── 5. Cài Nginx ───────────────────────────────────────────────
if ! command -v nginx &>/dev/null; then
  echo "[5/7] Installing Nginx..."
  apt-get update -qq && apt-get install -y nginx certbot python3-certbot-nginx
else
  echo "[5/7] Nginx already installed"
fi

# Copy Nginx config
if [ -f "$SCRIPT_DIR/../nginx/agrishrimp.conf" ]; then
  cp "$SCRIPT_DIR/../nginx/agrishrimp.conf" "$NGINX_CONF"
  ln -sf "$NGINX_CONF" /etc/nginx/sites-enabled/agrishrimp
  nginx -t && systemctl reload nginx
  echo "✅ Nginx configured"
fi

# ── 6. Cấu hình SSH deploy key ─────────────────────────────────
echo "[6/7] SSH deploy key info:"
echo "    Add VPS_SSH_KEY secret to GitHub Actions:"
echo "    → Run: cat ~/.ssh/id_ed25519 (nếu đã có)"
echo "    → Hoặc tạo mới: ssh-keygen -t ed25519 -C 'github-actions-deploy'"
echo "    → Thêm public key vào ~/.ssh/authorized_keys"

# ── 7. GHCR login (dùng GitHub PAT read:packages) ─────────────
echo "[7/7] GHCR login setup:"
echo "    Tạo GitHub PAT với scope: read:packages"
echo "    Thêm vào GitHub Secrets với tên: GHCR_READ_TOKEN"
echo "    Trên VPS có thể login thủ công:"
echo "    → echo \"\$TOKEN\" | docker login ghcr.io -u USERNAME --password-stdin"

echo ""
echo "======================================"
echo "  Setup complete!"
echo ""
echo "  Checklist trước khi deploy lần đầu:"
echo "  [ ] Điền .env: $DEPLOY_DIR/.env"
echo "  [ ] Thêm secrets vào GitHub Actions (xem README)"
echo "  [ ] Cấu hình SSL: certbot --nginx -d agrishrimp.io.vn -d api.agrishrimp.io.vn"
echo "  [ ] Khởi tạo stack lần đầu: cd $DEPLOY_DIR && docker compose -f docker-compose.prod.yml up -d"
echo "======================================"
