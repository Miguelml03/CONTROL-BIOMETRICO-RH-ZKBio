// ============================================================
// PRORRIDRE — empleados.js  (Módulo de Gestión de Empleados)
// VISTA 5: Padrón Institucional y Control de Acceso por Rol
// ============================================================

const Empleados = (() => {
  let listaEmpleados = [];
  let listaDepartamentos = [];
  let editandoId = null;

  async function cargar(params = {}) {
    const tbody = document.getElementById('tbody-empleados');
    if (tbody) {
      tbody.innerHTML = `<tr><td colspan="7" style="text-align:center;padding:2rem;color:var(--txt-muted);">Cargando nómina de empleados…</td></tr>`;
    }

    try {
      // 1. Cargar departamentos si aún no están en memoria
      if (listaDepartamentos.length === 0) {
        listaDepartamentos = await Api.getDepartamentosMaestros(false).catch(() => []);
        poblarSelectDepartamentos();
      }

      // 2. Cargar empleados desde la API
      listaEmpleados = await Api.getEmpleados(params);
      renderTabla(listaEmpleados);

      // 3. Adaptar interfaz según rol
      adaptarSegunRol();

    } catch (err) {
      console.error('[Empleados]', err);
      if (tbody) {
        tbody.innerHTML = `<tr><td colspan="7" style="text-align:center;padding:2rem;color:#b91c1c;">Error al cargar empleados: ${err.message}</td></tr>`;
      }
    }
  }

  function renderTabla(empleados) {
    const tbody = document.getElementById('tbody-empleados');
    if (!tbody) return;

    if (!empleados || empleados.length === 0) {
      tbody.innerHTML = `<tr><td colspan="7" style="text-align:center;padding:2.5rem;color:var(--txt-muted);">No se encontraron empleados con los filtros seleccionados.</td></tr>`;
      document.getElementById('total-empleados-padron').textContent = '0 empleados';
      return;
    }

    document.getElementById('total-empleados-padron').textContent = `${empleados.length} empleados registrados`;

    const user = Api.getUsuarioActual();
    const esAdmin = user.esAdmin;

    let html = '';
    empleados.forEach(e => {
      const activo = e.activo !== false;
      const badgeEstado = activo
        ? `<span class="badge" style="background:#dcfce7;color:#15803d;padding:.2rem .5rem;border-radius:4px;font-size:.73rem;font-weight:600;">ACTIVO</span>`
        : `<span class="badge" style="background:#fee2e2;color:#991b1b;padding:.2rem .5rem;border-radius:4px;font-size:.73rem;font-weight:600;">INACTIVO</span>`;

      const sueldoFmt = e.sueldoMensual != null
        ? Number(e.sueldoMensual).toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
        : '3,000.00';

      const acciones = esAdmin ? `
        <div style="display:flex;gap:.4rem;justify-content:center;">
          <button class="btn-sm" style="background:#0284c7;color:#fff;border:none;padding:.3rem .6rem;border-radius:4px;cursor:pointer;font-size:.75rem;" onclick="Empleados.abrirModalEditar('${e.empId}')">
            Editar
          </button>
          ${activo ? `
            <button class="btn-sm" style="background:#ef4444;color:#fff;border:none;padding:.3rem .6rem;border-radius:4px;cursor:pointer;font-size:.75rem;" onclick="Empleados.desactivar('${e.empId}')">
              Desactivar
            </button>
          ` : `
            <button class="btn-sm" style="background:#16a34a;color:#fff;border:none;padding:.3rem .6rem;border-radius:4px;cursor:pointer;font-size:.75rem;" onclick="Empleados.activar('${e.empId}')">
              Activar
            </button>
          `}
        </div>
      ` : `<span style="font-size:.75rem;color:var(--txt-muted);">Solo Lectura</span>`;

      html += `
        <tr style="${!activo ? 'opacity:0.6;background:#f8fafc;' : ''}">
          <td style="font-weight:600;font-family:monospace;color:#0284c7;">${e.empId}</td>
          <td>
            <div style="font-weight:600;color:#0f172a;">${e.nombreCompleto || 'EMPLEADO DESCONOCIDO'}</div>
            <div style="font-size:.72rem;color:var(--txt-muted);">${e.apellidos || ''}, ${e.nombres || ''}</div>
          </td>
          <td><span style="font-size:.82rem;color:#334155;">${e.cargo || 'Personal'}</span></td>
          <td>
            <div style="font-weight:500;font-size:.8rem;color:#0f172a;">${e.departamentoNombre || 'GENERAL'}</div>
            <div style="font-size:.7rem;color:var(--txt-muted);">${e.departamentoRuta || ''}</div>
          </td>
          <td style="text-align:right;font-weight:600;font-family:monospace;">S/ ${sueldoFmt}</td>
          <td style="text-align:center;">${badgeEstado}</td>
          <td style="text-align:center;">${acciones}</td>
        </tr>
      `;
    });

    tbody.innerHTML = html;
  }

  function poblarSelectDepartamentos() {
    const sel = document.getElementById('emp-modal-depto');
    if (!sel) return;
    sel.innerHTML = '<option value="">Seleccione Departamento / Meta…</option>';
    listaDepartamentos.forEach(d => {
      const opt = document.createElement('option');
      opt.value = d.id;
      const indent = d.nivel > 0 ? '— '.repeat(d.nivel) : '';
      opt.textContent = `${indent}${d.nombre}`;
      sel.appendChild(opt);
    });
  }

  function abrirModalNuevo() {
    const user = Api.getUsuarioActual();
    if (!user.esAdmin) {
      Toast.error('Acceso Denegado: Su rol (RRHH) no tiene permisos para crear empleados.');
      return;
    }

    editandoId = null;
    document.getElementById('emp-modal-titulo').textContent = 'Nuevo Empleado — Padrón PRORRIDRE';
    document.getElementById('emp-modal-empid').value = '';
    document.getElementById('emp-modal-empid').disabled = false;
    document.getElementById('emp-modal-nombres').value = '';
    document.getElementById('emp-modal-apellidos').value = '';
    document.getElementById('emp-modal-cargo').value = '';
    document.getElementById('emp-modal-sueldo').value = '3000.00';
    document.getElementById('emp-modal-depto').value = '';
    document.getElementById('emp-modal-activo').checked = true;

    document.getElementById('modal-empleado').style.display = 'flex';
  }

  function abrirModalEditar(empId) {
    const user = Api.getUsuarioActual();
    if (!user.esAdmin) {
      Toast.error('Acceso Denegado: Su rol (RRHH) no tiene permisos para editar empleados.');
      return;
    }

    const emp = listaEmpleados.find(e => e.empId === empId);
    if (!emp) return;

    editandoId = empId;
    document.getElementById('emp-modal-titulo').textContent = `Editar Empleado — ID ${empId}`;
    document.getElementById('emp-modal-empid').value = emp.empId;
    document.getElementById('emp-modal-empid').disabled = true;
    document.getElementById('emp-modal-nombres').value = emp.nombres || '';
    document.getElementById('emp-modal-apellidos').value = emp.apellidos || '';
    document.getElementById('emp-modal-cargo').value = emp.cargo || '';
    document.getElementById('emp-modal-sueldo').value = emp.sueldoMensual || '3000.00';
    document.getElementById('emp-modal-depto').value = emp.departamentoId || '';
    document.getElementById('emp-modal-activo').checked = emp.activo !== false;

    document.getElementById('modal-empleado').style.display = 'flex';
  }

  function cerrarModal() {
    const modal = document.getElementById('modal-empleado');
    if (modal) modal.style.display = 'none';
  }

  async function guardar() {
    const empId = document.getElementById('emp-modal-empid').value.trim();
    const nombres = document.getElementById('emp-modal-nombres').value.trim();
    const apellidos = document.getElementById('emp-modal-apellidos').value.trim();
    const cargo = document.getElementById('emp-modal-cargo').value.trim();
    const sueldo = parseFloat(document.getElementById('emp-modal-sueldo').value) || 3000.00;
    const deptoId = document.getElementById('emp-modal-depto').value;
    const activo = document.getElementById('emp-modal-activo').checked;

    if (!empId) {
      Toast.error('El código/ID de empleado es obligatorio.');
      return;
    }

    const payload = {
      empId,
      nombres,
      apellidos,
      cargo,
      sueldoMensual: sueldo,
      departamentoId: deptoId ? parseInt(deptoId) : null,
      activo
    };

    try {
      if (editandoId) {
        await Api.actualizarEmpleado(editandoId, payload);
        Toast.ok(`Empleado ${empId} actualizado exitosamente.`);
      } else {
        await Api.crearEmpleado(payload);
        Toast.ok(`Empleado ${empId} creado exitosamente en el padrón.`);
      }
      cerrarModal();
      cargar();
    } catch (err) {
      Toast.error('Error al guardar empleado: ' + err.message);
    }
  }

  async function desactivar(empId) {
    if (!confirm(`¿Está seguro de desactivar al empleado ID ${empId}? Ya no aparecerá en el cálculo activo de planillas.`)) {
      return;
    }
    try {
      await Api.desactivarEmpleado(empId);
      Toast.ok(`Empleado ${empId} desactivado.`);
      cargar();
    } catch (err) {
      Toast.error('Error: ' + err.message);
    }
  }

  async function activar(empId) {
    try {
      await Api.activarEmpleado(empId);
      Toast.ok(`Empleado ${empId} reactivado.`);
      cargar();
    } catch (err) {
      Toast.error('Error: ' + err.message);
    }
  }

  function filtrar() {
    const txt = (document.getElementById('padron-search')?.value || '').trim().toUpperCase();
    const depto = document.getElementById('padron-filtro-depto')?.value || '';
    const estado = document.getElementById('padron-filtro-estado')?.value || '';

    let filtrados = listaEmpleados;
    if (txt) {
      filtrados = filtrados.filter(e =>
        e.empId.toUpperCase().includes(txt) ||
        (e.nombreCompleto && e.nombreCompleto.toUpperCase().includes(txt)) ||
        (e.cargo && e.cargo.toUpperCase().includes(txt))
      );
    }
    if (depto) {
      filtrados = filtrados.filter(e => e.departamentoNombre && e.departamentoNombre.toUpperCase() === depto.toUpperCase());
    }
    if (estado !== '') {
      const act = estado === 'true';
      filtrados = filtrados.filter(e => (e.activo !== false) === act);
    }

    renderTabla(filtrados);
  }

  function adaptarSegunRol() {
    const user = Api.getUsuarioActual();
    const btnNuevo = document.getElementById('btn-nuevo-empleado');
    if (btnNuevo) {
      btnNuevo.style.display = user.esAdmin ? 'inline-flex' : 'none';
    }
  }

  return {
    cargar,
    abrirModalNuevo,
    abrirModalEditar,
    cerrarModal,
    guardar,
    desactivar,
    activar,
    filtrar,
    adaptarSegunRol
  };
})();

