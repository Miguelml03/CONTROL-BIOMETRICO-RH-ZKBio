// ============================================================
// PRORRIDRE — usuarios.js (Gestión de Usuarios y Roles del Sistema)
// Permite al Administrador (Doctor / Director) asignar nuevos usuarios,
// modificar roles (ROLE_ADMIN vs ROLE_RRHH) y cambiar credenciales.
// ============================================================

const Usuarios = (() => {
  let listaUsuarios = [];
  let editandoId = null;

  async function cargar() {
    const tbody = document.getElementById('tbody-usuarios');
    if (tbody) {
      tbody.innerHTML = `<tr><td colspan="6" style="text-align:center;padding:2rem;color:var(--txt-muted);">Cargando usuarios del sistema…</td></tr>`;
    }

    try {
      listaUsuarios = await Api.getUsuarios();
      renderTabla(listaUsuarios);
    } catch (err) {
      console.error('[Usuarios]', err);
      if (tbody) {
        tbody.innerHTML = `<tr><td colspan="6" style="text-align:center;padding:2rem;color:#b91c1c;">Error al cargar usuarios: ${err.message}</td></tr>`;
      }
    }
  }

  function renderTabla(usuarios) {
    const tbody = document.getElementById('tbody-usuarios');
    if (!tbody) return;

    if (!usuarios || usuarios.length === 0) {
      tbody.innerHTML = `<tr><td colspan="6" style="text-align:center;padding:2.5rem;color:var(--txt-muted);">No hay usuarios registrados.</td></tr>`;
      document.getElementById('total-usuarios-badge').textContent = '0 usuarios';
      return;
    }

    document.getElementById('total-usuarios-badge').textContent = `${usuarios.length} usuarios registrados`;

    let html = '';
    usuarios.forEach(u => {
      const activo = u.activo !== false;
      const esAdmin = u.rol === 'ROLE_ADMIN';
      const badgeRol = esAdmin
        ? `<span class="badge" style="background:#e0f2fe;color:#0369a1;padding:.2rem .55rem;border-radius:4px;font-size:.74rem;font-weight:700;">ADMINISTRADOR (TOTAL)</span>`
        : `<span class="badge" style="background:#fef3c7;color:#b45309;padding:.2rem .55rem;border-radius:4px;font-size:.74rem;font-weight:700;">RRHH (SOLO LECTURA)</span>`;

      const badgeEstado = activo
        ? `<span class="badge" style="background:#dcfce7;color:#15803d;padding:.2rem .5rem;border-radius:4px;font-size:.73rem;font-weight:600;">ACTIVO</span>`
        : `<span class="badge" style="background:#fee2e2;color:#991b1b;padding:.2rem .5rem;border-radius:4px;font-size:.73rem;font-weight:600;">INACTIVO</span>`;

      const esCuentaSistema = ['doctor', 'admin'].includes((u.username || '').toLowerCase());

      html += `
        <tr style="${!activo ? 'opacity:0.6;background:#f8fafc;' : ''}">
          <td style="font-family:monospace;font-weight:600;color:#0284c7;">#${u.id}</td>
          <td style="font-family:monospace;font-weight:700;color:#0f172a;">${u.username}</td>
          <td style="font-weight:600;color:#1e293b;">${u.nombreCompleto || '—'}</td>
          <td>${badgeRol}</td>
          <td style="text-align:center;">${badgeEstado}</td>
          <td style="text-align:center;">
            <div style="display:flex;gap:.4rem;justify-content:center;">
              <button class="btn-sm" style="background:#0284c7;color:#fff;border:none;padding:.3rem .6rem;border-radius:4px;cursor:pointer;font-size:.75rem;" onclick="Usuarios.abrirModalEditar(${u.id})">
                Editar
              </button>
              ${!esCuentaSistema ? `
                <button class="btn-sm" style="background:#ef4444;color:#fff;border:none;padding:.3rem .6rem;border-radius:4px;cursor:pointer;font-size:.75rem;" onclick="Usuarios.eliminar(${u.id})">
                  Eliminar
                </button>
              ` : `
                <span style="font-size:.72rem;color:var(--txt-muted);padding:.3rem .4rem;" title="Cuenta del sistema protegida">Protegida</span>
              `}
            </div>
          </td>
        </tr>
      `;
    });

    tbody.innerHTML = html;
  }

  function abrirModalNuevo() {
    editandoId = null;
    document.getElementById('user-modal-titulo').textContent = 'Asignar Nuevo Usuario del Sistema';
    document.getElementById('user-modal-username').value = '';
    document.getElementById('user-modal-username').disabled = false;
    document.getElementById('user-modal-nombre').value = '';
    document.getElementById('user-modal-pass').value = '';
    document.getElementById('user-modal-pass-label').textContent = 'Contraseña *';
    document.getElementById('user-modal-rol').value = 'ROLE_RRHH';
    document.getElementById('user-modal-activo').checked = true;

    document.getElementById('modal-usuario').style.display = 'flex';
  }

  function abrirModalEditar(id) {
    const u = listaUsuarios.find(item => item.id === id);
    if (!u) return;

    editandoId = id;
    document.getElementById('user-modal-titulo').textContent = `Editar Usuario — ${u.username}`;
    document.getElementById('user-modal-username').value = u.username;
    document.getElementById('user-modal-username').disabled = true;
    document.getElementById('user-modal-nombre').value = u.nombreCompleto || '';
    document.getElementById('user-modal-pass').value = '';
    document.getElementById('user-modal-pass-label').textContent = 'Nueva Contraseña (dejar en blanco para conservar)';
    document.getElementById('user-modal-rol').value = u.rol || 'ROLE_RRHH';
    document.getElementById('user-modal-activo').checked = u.activo !== false;

    document.getElementById('modal-usuario').style.display = 'flex';
  }

  function cerrarModal() {
    const m = document.getElementById('modal-usuario');
    if (m) m.style.display = 'none';
  }

  async function guardar() {
    const username = document.getElementById('user-modal-username').value.trim();
    const nombreCompleto = document.getElementById('user-modal-nombre').value.trim();
    const password = document.getElementById('user-modal-pass').value.trim();
    const rol = document.getElementById('user-modal-rol').value;
    const activo = document.getElementById('user-modal-activo').checked;

    if (!editandoId && !username) {
      Toast.error('El nombre de usuario es obligatorio.');
      return;
    }

    if (!editandoId && (!password || password.length < 4)) {
      Toast.error('La contraseña debe tener al menos 4 caracteres.');
      return;
    }

    const payload = {
      username,
      nombreCompleto,
      rol,
      activo
    };
    if (password) {
      payload.password = password;
    }

    try {
      if (editandoId) {
        await Api.actualizarUsuario(editandoId, payload);
        Toast.ok(`Usuario actualizado exitosamente.`);
      } else {
        await Api.crearUsuario(payload);
        Toast.ok(`Usuario ${username} creado exitosamente.`);
      }
      cerrarModal();
      cargar();
    } catch (err) {
      Toast.error('Error al guardar usuario: ' + err.message);
    }
  }

  async function eliminar(id) {
    const u = listaUsuarios.find(item => item.id === id);
    const nombre = u ? u.username : id;
    if (!confirm(`¿Está seguro de eliminar al usuario '${nombre}'? Esta acción es irreversible.`)) {
      return;
    }

    try {
      await Api.eliminarUsuario(id);
      Toast.ok(`Usuario ${nombre} eliminado.`);
      cargar();
    } catch (err) {
      Toast.error('Error al eliminar: ' + err.message);
    }
  }

  return {
    cargar,
    abrirModalNuevo,
    abrirModalEditar,
    cerrarModal,
    guardar,
    eliminar
  };
})();

