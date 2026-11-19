// =============================================================================
// PRORRIDRE — pagos-doctor.js  (FASE 4: Panel del Doctor y Planillas)
// Gestiona el cálculo financiero, reglas en vivo, edición inline de sueldos
// y generación de Boletas de Liquidación de Asistencia.
// =============================================================================

const PagosDoctor = (() => {

  let _planillasData = [];
  let _currentParams = {};
  let _empleadoSeleccionado = null;
  let _registrosEmpleado = [];

  // Reglas globales en vivo ajustables por el Doctor
  const reglas = {
    horaBase: '08:00:00',
    horaTolerancia: '08:10:00',
    descontarTardanza: true,
    descontarDeficit: true,
    penalizarOmision: true,
    excedenteCompensa: true
  };

  /**
   * Carga la planilla general aplicando los filtros actuales.
   */
  async function cargar(params = {}) {
    _currentParams = { ...params };
    _mostrarCargando();

    try {
      const data = await Api.getPlanillas(params);
      _planillasData = Array.isArray(data) ? data : [];
      renderizarTodo();
    } catch (err) {
      console.error('[PagosDoctor] Error al cargar planillas:', err);
      _mostrarError('Error al cargar datos de planilla: ' + err.message);
    }
  }

  function renderizarTodo() {
    actualizarKpisFinancieros();
    renderizarTabla();
  }

  /**
   * Actualiza las 4 Tarjetas Financieras Superiores.
   */
  function actualizarKpisFinancieros() {
    let sumBruto = 0;
    let sumDctoTardanzas = 0;
    let sumDctoDeficitFaltas = 0;
    let sumNeto = 0;

    _planillasData.forEach(p => {
      const sueldo = Number(p.sueldoMensual) || 0;
      sumBruto += sueldo;

      const dTard = reglas.descontarTardanza ? (Number(p.descuentoTardanzas) || 0) : 0;
      sumDctoTardanzas += dTard;

      const dDef = reglas.descontarDeficit ? (Number(p.descuentoDeficitHoras) || 0) : 0;
      const dFal = Number(p.descuentoFaltas) || 0;
      const dOmi = reglas.penalizarOmision ? (Number(p.penalidadOmisionMarcado) || 0) : 0;
      sumDctoDeficitFaltas += (dDef + dFal + dOmi);

      // Si las reglas en vivo alteran descuentos, recalculamos neto
      const totalDcto = dTard + (dDef + dFal + dOmi);
      const bonif = Number(p.bonificacionHorasExtra) || 0;
      const neto = Math.max(0, sueldo - totalDcto + bonif);
      sumNeto += neto;
    });

    document.getElementById('fin-kpi-bruto').textContent = formatSoles(sumBruto);
    document.getElementById('fin-kpi-tardanza').textContent = '- ' + formatSoles(sumDctoTardanzas);
    document.getElementById('fin-kpi-deficit-faltas').textContent = '- ' + formatSoles(sumDctoDeficitFaltas);
    document.getElementById('fin-kpi-neto').textContent = formatSoles(sumNeto);
    document.getElementById('fin-kpi-trabajadores').textContent = `${_planillasData.length} trabajadores evaluados`;
  }

  /**
   * Renderiza la tabla de liquidación con inputs inline para sueldos.
   */
  function renderizarTabla() {
    const tbody = document.getElementById('planillas-tbody');
    if (!tbody) return;

    if (_planillasData.length === 0) {
      tbody.innerHTML = `
        <tr><td colspan="14">
          <div class="empty-state">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
            <p>No se encontraron registros de liquidación para el período o departamento seleccionado.</p>
          </div>
        </td></tr>`;
      return;
    }

    let html = '';
    _planillasData.forEach(p => {
      const sueldo = Number(p.sueldoMensual) || 3000;
      const dTard = reglas.descontarTardanza ? (Number(p.descuentoTardanzas) || 0) : 0;
      const dDef = reglas.descontarDeficit ? (Number(p.descuentoDeficitHoras) || 0) : 0;
      const dFal = Number(p.descuentoFaltas) || 0;
      const dOmi = reglas.penalizarOmision ? (Number(p.penalidadOmisionMarcado) || 0) : 0;
      const dDeficitFaltas = dDef + dFal + dOmi;
      const bonif = Number(p.bonificacionHorasExtra) || 0;
      const neto = Math.max(0, sueldo - (dTard + dDeficitFaltas) + bonif);

      const numJustificaciones = (p.justificaciones && p.justificaciones.length) || 0;
      const justBadge = numJustificaciones > 0
        ? `<span class="badge badge-reglamentaria" title="${numJustificaciones} día(s) justificado(s)">✓ ${numJustificaciones} Justif.</span>`
        : `<span style="color:var(--txt-muted);font-size:.75rem">—</span>`;

      html += `
        <tr id="row-emp-${p.empId}">
          <td style="font-weight:600;font-family:monospace">EMP-${p.empId}</td>
          <td>
            <strong>${p.nombreCompleto || 'EMPLEADO ' + p.empId}</strong>
            <div style="font-size:.7rem;color:var(--txt-muted)">${p.cargo || 'Personal'}</div>
          </td>
          <td><span class="badge badge-depto">${p.departamento || 'GENERAL'}</span></td>
          <td style="width:130px">
            <div class="sueldo-inline-wrap">
              <span style="font-size:.75rem;color:var(--txt-muted)">S/</span>
              <input type="number" step="50" min="0" class="input-sueldo-inline"
                     id="sueldo-input-${p.empId}"
                     value="${sueldo.toFixed(2)}"
                     onblur="PagosDoctor.actualizarSueldoInline('${p.empId}', this.value)"
                     onkeydown="if(event.key==='Enter') this.blur()"
                     title="Presione Enter o salga del campo para guardar" />
            </div>
          </td>
          <td style="text-align:center">${p.diasConRegistro} / ${p.diasEvaluados}</td>
          <td style="text-align:center">${p.diasFalta > 0 ? `<span class="badge badge-deficit" style="font-size:.72rem">${p.diasFalta} faltas</span>` : '0'}</td>
          <td style="text-align:right">${p.minutosTardanzaNetosNoJustificados > 0 ? `<span style="color:var(--rojo);font-weight:600">${p.minutosTardanzaNetosNoJustificados} min</span>` : '0 min'}</td>
          <td style="text-align:right">${p.horasDeficitNetas > 0 ? `<span style="color:var(--rojo)">${p.horasDeficitNetas.toFixed(2)} h</span>` : '0.00 h'}</td>
          <td style="text-align:right">${p.totalHorasExcedente > 0 ? `<span style="color:var(--verde);font-weight:600">+${p.totalHorasExcedente.toFixed(2)} h</span>` : '0.00 h'}</td>
          <td style="text-align:right;color:var(--rojo)">${dTard > 0 ? '- ' + formatSoles(dTard) : 'S/ 0.00'}</td>
          <td style="text-align:right;color:var(--rojo)">${dDeficitFaltas > 0 ? '- ' + formatSoles(dDeficitFaltas) : 'S/ 0.00'}</td>
          <td style="text-align:center">${justBadge}</td>
          <td style="text-align:right">
            <span class="neto-highlight">${formatSoles(neto)}</span>
          </td>
          <td style="text-align:center">
            <button class="btn-action-doctor btn-boleta" onclick="PagosDoctor.abrirBoleta('${p.empId}')" title="Ver desglose matemático y boleta">
              📄 Boleta
            </button>
          </td>
        </tr>`;
    });

    tbody.innerHTML = html;
  }

  /**
   * Guarda el sueldo editado inline y actualiza la fila al instante.
   */
  async function actualizarSueldoInline(empId, nuevoSueldoStr) {
    const nuevoSueldo = parseFloat(nuevoSueldoStr);
    if (isNaN(nuevoSueldo) || nuevoSueldo < 0) {
      Toast.warn('Ingrese un monto de sueldo válido.');
      return;
    }

    try {
      await Api.updateConfig({
        empId: empId,
        sueldoMensual: nuevoSueldo
      });

      // Actualizar en el modelo local
      const emp = _planillasData.find(p => p.empId === empId);
      if (emp) {
        emp.sueldoMensual = nuevoSueldo;
        // Recalcular valores derivados
        const diasBase = 30;
        const valorDia = nuevoSueldo / diasBase;
        const valorHora = valorDia / 8;
        const valorMinuto = valorHora / 60;

        emp.valorDia = valorDia;
        emp.valorHora = valorHora;
        emp.valorMinuto = valorMinuto;

        // Recalcular descuentos
        emp.descuentoTardanzas = emp.minutosTardanzaNetosNoJustificados * valorMinuto;
        emp.descuentoDeficitHoras = emp.horasDeficitNetas * valorHora;
        emp.descuentoFaltas = emp.diasFalta * valorDia;

        let penalidad = 0;
        if (emp.cantidadOmisionMarcado > 0) {
          penalidad = (valorDia * 0.5) * emp.cantidadOmisionMarcado;
        }
        emp.penalidadOmisionMarcado = penalidad;

        const totalDcto = emp.descuentoTardanzas + emp.descuentoDeficitHoras + emp.descuentoFaltas + penalidad;
        emp.totalDescuentos = totalDcto;
        emp.netoAPagar = Math.max(0, nuevoSueldo - totalDcto + (Number(emp.bonificacionHorasExtra) || 0));
      }

      renderizarTodo();
      Toast.ok(`Sueldo de EMP-${empId} actualizado a S/ ${nuevoSueldo.toFixed(2)}.`);
    } catch (err) {
      console.error('[PagosDoctor] Error al actualizar sueldo:', err);
      Toast.error('No se pudo guardar el sueldo: ' + err.message);
    }
  }

  /**
   * Aplica un cambio en los switches de reglas en vivo del Doctor.
   */
  function cambiarRegla(nombreRegla, valor) {
    reglas[nombreRegla] = valor;
    Toast.ok(`Regla actualizada: ${nombreRegla} = ${valor ? 'Activado' : 'Desactivado'}`);
    renderizarTodo();
  }

  /**
   * Abre el Modal de Boleta de Liquidación con el desglose día por día.
   */
  async function abrirBoleta(empId) {
    _empleadoSeleccionado = _planillasData.find(p => p.empId === empId);
    if (!_empleadoSeleccionado) {
      Toast.warn('Datos del trabajador no encontrados.');
      return;
    }

    const modal = document.getElementById('modal-boleta');
    if (!modal) return;
    modal.classList.add('active');

    // Rellenar datos básicos
    const p = _empleadoSeleccionado;
    const sueldo = Number(p.sueldoMensual) || 3000;
    const valorDia = (sueldo / 30).toFixed(2);
    const valorHora = (sueldo / 30 / 8).toFixed(2);
    const valorMinuto = (sueldo / 30 / 8 / 60).toFixed(4);

    document.getElementById('b-nombre').textContent = p.nombreCompleto || 'EMPLEADO ' + p.empId;
    document.getElementById('b-empid').textContent = 'EMP-' + p.empId;
    document.getElementById('b-cargo').textContent = p.cargo || 'Personal PRORRIDRE';
    document.getElementById('b-depto').textContent = p.departamento || 'GENERAL';
    document.getElementById('b-periodo').textContent = `${p.periodoInicio || '—'} al ${p.periodoFin || '—'}`;

    // Resumen numérico
    document.getElementById('b-sueldo').textContent = formatSoles(sueldo);
    document.getElementById('b-val-dia').textContent = `S/ ${valorDia}`;
    document.getElementById('b-val-hora').textContent = `S/ ${valorHora}`;
    document.getElementById('b-val-min').textContent = `S/ ${valorMinuto}`;

    document.getElementById('b-dias-asist').textContent = `${p.diasConRegistro} de ${p.diasEvaluados} hábiles`;
    document.getElementById('b-faltas-txt').textContent = `${p.diasFalta} faltas (- S/ ${(p.diasFalta * (sueldo/30)).toFixed(2)})`;
    document.getElementById('b-tard-txt').textContent = `${p.minutosTardanzaNetosNoJustificados} min (- S/ ${Number(p.descuentoTardanzas).toFixed(2)})`;
    document.getElementById('b-def-txt').textContent = `${p.horasDeficitNetas.toFixed(2)} hrs (- S/ ${Number(p.descuentoDeficitHoras).toFixed(2)})`;
    document.getElementById('b-exced-txt').textContent = `+${p.totalHorasExcedente.toFixed(2)} hrs (+ S/ ${Number(p.bonificacionHorasExtra).toFixed(2)})`;

    document.getElementById('b-total-dcto').textContent = '- ' + formatSoles(p.totalDescuentos || 0);
    document.getElementById('b-neto').textContent = formatSoles(p.netoAPagar || 0);

    // Cargar historial de asistencia del mes de ese empleado
    const tbodyDias = document.getElementById('boleta-dias-tbody');
    tbodyDias.innerHTML = '<tr><td colspan="8" style="text-align:center;padding:1rem">Cargando días del trabajador…</td></tr>';

    try {
      const regs = await Api.getAsistencia({
        empId: p.empId,
        fechaInicio: p.periodoInicio,
        fechaFin: p.periodoFin
      });
      _registrosEmpleado = Array.isArray(regs) ? regs : [];

      if (_registrosEmpleado.length === 0) {
        tbodyDias.innerHTML = '<tr><td colspan="8" style="text-align:center;color:var(--txt-muted)">Sin marcaciones registradas en el período.</td></tr>';
      } else {
        let rows = '';
        _registrosEmpleado.forEach(r => {
          const tard = (r.minutosTardanza && r.minutosTardanza > 0) ? `<span style="color:var(--rojo);font-weight:600">${r.minutosTardanza}m</span>` : '0';
          const bal = r.balanceJornada != null
            ? (r.balanceJornada >= 0 ? `<span style="color:var(--verde)">+${r.balanceJornada.toFixed(2)}h</span>` : `<span style="color:var(--rojo)">${r.balanceJornada.toFixed(2)}h</span>`)
            : '—';

          const just = r.estadoJustificacion && r.estadoJustificacion !== 'NINGUNO'
            ? `<span class="badge badge-reglamentaria" style="font-size:.68rem">${r.estadoJustificacion}</span>`
            : '';

          rows += `
            <tr>
              <td style="font-family:monospace">${r.fecha || '—'}</td>
              <td>${r.horaEntrada || '<span style="color:var(--rojo)">Omitido</span>'}</td>
              <td>${r.inicioAlmuerzo || '—'}</td>
              <td>${r.finAlmuerzo || '—'}</td>
              <td>${r.horaSalida || '<span style="color:var(--rojo)">Omitido</span>'}</td>
              <td>${r.condicionIngreso || '—'}</td>
              <td style="text-align:center">${tard}</td>
              <td style="text-align:center">${bal}</td>
              <td>${just}</td>
            </tr>`;
        });
        tbodyDias.innerHTML = rows;
      }
    } catch (e) {
      tbodyDias.innerHTML = '<tr><td colspan="8" style="text-align:center;color:var(--rojo)">Error cargando registros diarios.</td></tr>';
    }
  }

  function cerrarBoleta() {
    const modal = document.getElementById('modal-boleta');
    if (modal) modal.classList.remove('active');
  }

  function imprimirBoleta() {
    window.print();
  }

  function formatSoles(val) {
    const n = Number(val) || 0;
    return 'S/ ' + n.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  function _mostrarCargando() {
    const tbody = document.getElementById('planillas-tbody');
    if (tbody) {
      tbody.innerHTML = `
        <tr><td colspan="14">
          <div class="loading-state">
            <div class="spinner-dark"></div>
            <p>Calculando liquidación de planilla y descuentos del Doctor…</p>
          </div>
        </td></tr>`;
    }
  }

  function _mostrarError(msg) {
    const tbody = document.getElementById('planillas-tbody');
    if (tbody) {
      tbody.innerHTML = `
        <tr><td colspan="14">
          <div class="empty-state" style="color:var(--rojo)">
            <p>${msg}</p>
          </div>
        </td></tr>`;
    }
  }

  return {
    cargar,
    actualizarSueldoInline,
    cambiarRegla,
    abrirBoleta,
    cerrarBoleta,
    imprimirBoleta
  };
})();

