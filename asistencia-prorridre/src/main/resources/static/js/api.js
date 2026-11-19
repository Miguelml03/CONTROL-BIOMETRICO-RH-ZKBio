// ============================================================
// PRORRIDRE — api.js  (Capa de comunicación con el backend)
// Soporta autenticación JWT Bearer y control de permisos por Rol.
// ============================================================
const API_BASE = '/api/v1';

const Api = (() => {
  function getToken() {
    return localStorage.getItem('prorridre_token');
  }

  async function request(url, opts = {}) {
    const headers = {
      'Content-Type': 'application/json',
      ...opts.headers
    };

    const token = getToken();
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const res = await fetch(url, { ...opts, headers });

    if (res.status === 401) {
      // Sesión expirada o token inválido
      localStorage.removeItem('prorridre_token');
      localStorage.removeItem('prorridre_username');
      localStorage.removeItem('prorridre_nombre');
      localStorage.removeItem('prorridre_rol');
      if (!window.location.pathname.endsWith('login.html')) {
        window.location.href = '/login.html';
      }
      throw new Error('401: Sesión no autorizada o expirada.');
    }

    if (res.status === 403) {
      if (typeof Toast !== 'undefined' && Toast.error) {
        Toast.error('Acceso Denegado: Su rol (RRHH) solo tiene permisos de lectura.');
      }
      throw new Error('403: Acceso denegado (Solo lectura).');
    }

    if (!res.ok) {
      const txt = await res.text().catch(() => 'Error desconocido');
      throw new Error(`${res.status}: ${txt}`);
    }

    if (res.status === 204) return null;
    return res.json();
  }

  function buildQs(params = {}) {
    const qs = new URLSearchParams(
      Object.entries(params).filter(([, v]) => v != null && v !== '')
    );
    const s = qs.toString();
    return s ? '?' + s : '';
  }

  return {
    // ── Autenticación y Sesión ────────────────────────────────
    getToken,
    getUsuarioActual() {
      const rol = localStorage.getItem('prorridre_rol') || '';
      return {
        username: localStorage.getItem('prorridre_username') || 'Invitado',
        nombreCompleto: localStorage.getItem('prorridre_nombre') || 'Usuario',
        rol: rol,
        esAdmin: rol === 'ROLE_ADMIN',
        esRrhh: rol === 'ROLE_RRHH'
      };
    },
    logout() {
      localStorage.removeItem('prorridre_token');
      localStorage.removeItem('prorridre_username');
      localStorage.removeItem('prorridre_nombre');
      localStorage.removeItem('prorridre_rol');
      window.location.href = '/login.html';
    },

    // ── Asistencia ──────────────────────────────────────────
    getAsistencia(params = {}) {
      return request(`${API_BASE}/asistencia${buildQs(params)}`);
    },
    sincronizar() {
      return request(`${API_BASE}/asistencia/sincronizar`, { method: 'POST' });
    },
    justificarRegistro(id, tipo, observacion, aplicadoPor = 'DOCTOR') {
      return request(`${API_BASE}/asistencia/${id}/justificar`, {
        method: 'POST',
        body: JSON.stringify({ tipoJustificacion: tipo, observacion, aplicadoPor })
      });
    },
    editarAsistencia(id, payload) {
      return request(`${API_BASE}/asistencia/${id}`, {
        method: 'PUT',
        body: JSON.stringify(payload)
      });
    },
    eliminarAsistencia(id) {
      return request(`${API_BASE}/asistencia/${id}`, {
        method: 'DELETE'
      });
    },

    // ── Dashboard ───────────────────────────────────────────
    getDashboard(params = {}) {
      return request(`${API_BASE}/dashboard/resumen${buildQs(params)}`);
    },

    // ── Departamentos y Fechas ──────────────────────────────
    getDepartamentos() {
      return request(`${API_BASE}/asistencia/departamentos`);
    },
    getFechas() {
      return request(`${API_BASE}/asistencia/fechas`);
    },

    // ── Maestros (Departamentos y Empleados) ─────────────────
    sincronizarMaestros() {
      return request(`${API_BASE}/sincronizar-maestros`, { method: 'POST' });
    },
    getDepartamentosMaestros(arbol = false) {
      return request(`${API_BASE}/maestros/departamentos?arbol=${arbol}`);
    },

    // ── Gestión del Padrón de Empleados (FASE 5) ─────────────
    getEmpleados(params = {}) {
      return request(`${API_BASE}/empleados${buildQs(params)}`);
    },
    getEmpleado(empId) {
      return request(`${API_BASE}/empleados/${encodeURIComponent(empId)}`);
    },
    crearEmpleado(payload) {
      return request(`${API_BASE}/empleados`, {
        method: 'POST',
        body: JSON.stringify(payload)
      });
    },
    actualizarEmpleado(empId, payload) {
      return request(`${API_BASE}/empleados/${encodeURIComponent(empId)}`, {
        method: 'PUT',
        body: JSON.stringify(payload)
      });
    },
    desactivarEmpleado(empId) {
      return request(`${API_BASE}/empleados/${encodeURIComponent(empId)}`, {
        method: 'DELETE'
      });
    },
    activarEmpleado(empId) {
      return request(`${API_BASE}/empleados/${encodeURIComponent(empId)}/activar`, {
        method: 'PUT'
      });
    },

    // ── Planillas y Descuentos del Doctor ────────────────────
    getPlanillas(params = {}) {
      return request(`${API_BASE}/planillas${buildQs(params)}`);
    },
    getPlanillaIndividual(empId, params = {}) {
      return request(`${API_BASE}/planillas/${encodeURIComponent(empId)}${buildQs(params)}`);
    },
    getConfig(empId) {
      return request(`${API_BASE}/planillas/config/${encodeURIComponent(empId)}`);
    },
    updateConfig(payload) {
      return request(`${API_BASE}/planillas/config`, {
        method: 'PUT',
        body: JSON.stringify(payload)
      });
    },

    // ── Usuarios del Sistema (Control de Acceso) ───────────
    getUsuarios() {
      return request(`${API_BASE}/usuarios`);
    },
    getUsuario(id) {
      return request(`${API_BASE}/usuarios/${id}`);
    },
    crearUsuario(payload) {
      return request(`${API_BASE}/usuarios`, {
        method: 'POST',
        body: JSON.stringify(payload)
      });
    },
    actualizarUsuario(id, payload) {
      return request(`${API_BASE}/usuarios/${id}`, {
        method: 'PUT',
        body: JSON.stringify(payload)
      });
    },
    eliminarUsuario(id) {
      return request(`${API_BASE}/usuarios/${id}`, {
        method: 'DELETE'
      });
    },

    // ── Exportación Excel Auditado ───────────────────────────
    getExportExcelUrl(params = {}) {
      const p = { ...params };
      const token = getToken();
      if (token) p.token = token;
      return `${API_BASE}/exportar/excel${buildQs(p)}`;
    },
    async descargarExcel(params = {}) {
      const token = getToken();
      const p = { ...params };
      if (token) p.token = token;
      const url = `${API_BASE}/exportar/excel${buildQs(p)}`;
      const headers = {};
      if (token) headers['Authorization'] = `Bearer ${token}`;

      try {
        const res = await fetch(url, { headers });
        if (!res.ok) {
          throw new Error(`Error ${res.status}: Falló la descarga del reporte.`);
        }
        const blob = await res.blob();
        const blobUrl = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = blobUrl;
        a.download = `Reporte_Biometrico_PRORRIDRE_${new Date().toISOString().slice(0, 10)}.xlsx`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(blobUrl);
      } catch (err) {
        if (typeof Toast !== 'undefined' && Toast.error) {
          Toast.error('Error al exportar Excel: ' + err.message);
        } else {
          alert('Error al exportar Excel: ' + err.message);
        }
      }
    }
  };
})();
