// ============================================================
// PRORRIDRE — asistencia.js
// Matriz de auditoría biométrica: 18 columnas A:R + modal Doctor
// ============================================================
const Asistencia = (() => {
  let todosLosRegistros = [];   // dataset completo
  let registrosFiltrados = [];  // después de filtrar por pestaña
  let sortCol = 'fecha';
  let sortDir = 'desc';
  let paginaActual = 1;
  const POR_PAGINA = 50;
  let filtroTab = 'todos';

  // ── Badges de condición de ingreso (Columna M) ──────────
  function badgeCondicion(cond) {
    if (!cond) return '<span class="badge badge-gris">—</span>';
    if (cond.includes('A Tiempo'))  return `<span class="badge badge-verde">✓ A Tiempo</span>`;
    if (cond.includes('Tardanza'))  return `<span class="badge badge-rojo">⚠ Tardanza</span>`;
    if (cond.includes('Omisión') || cond.includes('Sin Marcación'))
      return `<span class="badge badge-ambar">⊘ Omisión</span>`;
    return `<span class="badge badge-gris">${cond}</span>`;
  }

  // ── Badges de estado de cumplimiento (Columna R) ─────────
  function badgeEstado(estado) {
    if (!estado) return '<span class="badge badge-gris">—</span>';
    if (estado.includes('Excedente'))    return `<span class="badge badge-celeste">▲ Excedente</span>`;
    if (estado.includes('Reglamentaria'))return `<span class="badge badge-verde">✓ Reg.</span>`;
    if (estado.includes('Déficit'))      return `<span class="badge badge-ambar">▼ Déficit</span>`;
    if (estado.includes('Incompleto'))   return `<span class="badge badge-rojo">✖ Incompleto</span>`;
    if (estado.includes('Justificado'))  return `<span class="badge badge-morado">★ Justificado</span>`;
    if (estado.includes('Sabatina') || estado.includes('Sábado')) return `<span class="badge badge-azul">📅 Sabatino</span>`;
    return `<span class="badge badge-gris">${estado}</span>`;
  }

  // ── Badge de justificación del Doctor ───────────────────
  function badgeJustificacion(est) {
    if (!est || est === 'NINGUNO') return '';
    const labels = {
      JUSTIFICADO_MEDICO:       '🏥 Médico',
      COMISION_SERVICIO:        '🚗 Comisión',
      PAPELETA_PERMISO:         '📝 Permiso',
      REGULARIZADO_POR_DIRECTOR:'✅ Director',
      APLICAR_DESCUENTO:        '💸 Descuento'
    };
    const cls = est === 'APLICAR_DESCUENTO' ? 'badge-rojo' : 'badge-morado';
    return `<span class="badge ${cls}">${labels[est] || est}</span>`;
  }

  // ── Formateo de horas ────────────────────────────────────
  function fmtTime(t) { return t ? String(t).substring(0, 5) : '—'; }
  function fmtBalance(b) {
    if (b == null) return '<span class="balance-null">—</span>';
    const cls = b > 0 ? 'balance-pos' : (b < 0 ? 'balance-neg' : '');
    return `<span class="${cls}">${b > 0 ? '+' : ''}${Number(b).toFixed(2)} h</span>`;
  }

  // ── Filtros por pestaña ──────────────────────────────────
  function aplicarFiltroTab(data, tab) {
    switch (tab) {
      case 'tardanza':  return data.filter(r => r.condicionIngreso && r.condicionIngreso.includes('Tardanza'));
      case 'incompleto':return data.filter(r => r.estadoCumplimiento && r.estadoCumplimiento.includes('Incompleto') || r.numMarcaciones === 1);
      case 'deficit':   return data.filter(r => r.estadoCumplimiento && r.estadoCumplimiento.includes('Déficit'));
      case 'depurado':  return data.filter(r => r.numMarcaciones >= 5);
      default:          return data;
    }
  }

  // ── Ordenar dataset ──────────────────────────────────────
  function ordenar(data) {
    return [...data].sort((a, b) => {
      let va = a[sortCol], vb = b[sortCol];
      if (va == null) va = '';
      if (vb == null) vb = '';
      if (typeof va === 'number') return sortDir === 'asc' ? va - vb : vb - va;
      return sortDir === 'asc'
        ? String(va).localeCompare(String(vb))
        : String(vb).localeCompare(String(va));
    });
  }

  // ── Renderizar tabla ─────────────────────────────────────
  function renderTabla() {
    const ordenados = ordenar(registrosFiltrados);
    const total     = ordenados.length;
    const totalPag  = Math.max(1, Math.ceil(total / POR_PAGINA));
    paginaActual    = Math.min(paginaActual, totalPag);
    const inicio    = (paginaActual - 1) * POR_PAGINA;
    const pagina    = ordenados.slice(inicio, inicio + POR_PAGINA);

    const tbody = document.getElementById('asistencia-tbody');
    if (!tbody) return;

    if (pagina.length === 0) {
      tbody.innerHTML = `<tr><td colspan="20" style="text-align:center;padding:2rem;color:var(--txt-muted)">No hay registros en esta categoría.</td></tr>`;
    } else {
      tbody.innerHTML = pagina.map(r => {
        const justClase = (r.estadoJustificacion && r.estadoJustificacion !== 'NINGUNO') ? 'justified' : '';
        const btnClase  = (r.estadoJustificacion && r.estadoJustificacion !== 'NINGUNO') ? 'has-just' : '';
        return `
        <tr class="${justClase}" data-id="${r.id}">
          <td class="td-empid">${r.empId || '—'}</td>
          <td style="max-width:120px;overflow:hidden;text-overflow:ellipsis">${r.nombres || ''} ${r.apellidos || ''}</td>
          <td class="td-depto" title="${r.departamento || ''}">${r.departamento || '—'}</td>
          <td>${r.fecha || '—'}</td>
          <td style="text-align:center">${r.numMarcaciones ?? '—'}</td>
          <td style="max-width:140px;overflow:hidden;text-overflow:ellipsis;font-size:.72rem;color:var(--txt-muted)">${r.horasCrudas || '—'}</td>
          <td>${fmtTime(r.horaEntrada)}</td>
          <td>${fmtTime(r.inicioAlmuerzo)}</td>
          <td>${fmtTime(r.finAlmuerzo)}</td>
          <td>${fmtTime(r.horaSalida)}</td>
          <td>${badgeCondicion(r.condicionIngreso)}</td>
          <td>${r.minutosTardanza > 0 ? `<span class="tardanza-min">${r.minutosTardanza} min</span>` : '<span style="color:var(--txt-muted)">0</span>'}</td>
          <td>${r.jornadaEfectiva ? String(r.jornadaEfectiva).substring(0,5) : '—'}</td>
          <td>${r.jornadaNorma && r.jornadaNorma !== '--' ? String(r.jornadaNorma).substring(0,5) : '—'}</td>
          <td>${fmtBalance(r.balanceJornada)}</td>
          <td>${badgeEstado(r.estadoCumplimiento)}</td>
          <td>${r.almuerzoImputado ? '<span class="badge badge-azul">Imputado</span>' : ''}</td>
          <td>${badgeJustificacion(r.estadoJustificacion)}</td>
          <td>
            <div style="display:flex;gap:.35rem;justify-content:center;align-items:center;">
              <button class="btn-doctor ${btnClase}" onclick="Asistencia.abrirModal(${r.id})" title="Justificar Asistencia">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
                Doctor
              </button>
              ${Api.getUsuarioActual().esAdmin ? `
                <button class="btn-doctor" style="background:#0284c7;color:#fff;" onclick="Asistencia.abrirModalEditar(${r.id})" title="Editar horas marcadas">
                  ✏
                </button>
                <button class="btn-doctor" style="background:#ef4444;color:#fff;" onclick="Asistencia.eliminar(${r.id})" title="Eliminar registro">
                  🗑
                </button>
              ` : ''}
            </div>
          </td>
        </tr>`;
      }).join('');
    }

    // Footer de paginación
    const footer = document.getElementById('asistencia-footer');
    if (footer) {
      footer.innerHTML = `
        <span>Mostrando <strong>${inicio + 1}–${Math.min(inicio + POR_PAGINA, total)}</strong> de <strong>${total}</strong> registros</span>
        <div class="pagination">
          <button class="page-btn" onclick="Asistencia.irPagina(${paginaActual - 1})" ${paginaActual <= 1 ? 'disabled' : ''}>‹ Ant.</button>
          ${_paginaBotones(paginaActual, totalPag)}
          <button class="page-btn" onclick="Asistencia.irPagina(${paginaActual + 1})" ${paginaActual >= totalPag ? 'disabled' : ''}>Sig. ›</button>
        </div>`;
    }

    // Actualizar contadores de pestañas
    _actualizarContadores();
  }

  function _paginaBotones(actual, total) {
    let html = '';
    const max = 5;
    let ini = Math.max(1, actual - 2);
    let fin = Math.min(total, ini + max - 1);
    ini = Math.max(1, fin - max + 1);
    for (let i = ini; i <= fin; i++) {
      html += `<button class="page-btn ${i === actual ? 'active' : ''}" onclick="Asistencia.irPagina(${i})">${i}</button>`;
    }
    return html;
  }

  function _actualizarContadores() {
    const tabs = {
      todos:     todosLosRegistros.length,
      tardanza:  todosLosRegistros.filter(r => r.condicionIngreso && r.condicionIngreso.includes('Tardanza')).length,
      incompleto:todosLosRegistros.filter(r => (r.estadoCumplimiento && r.estadoCumplimiento.includes('Incompleto')) || r.numMarcaciones === 1).length,
      deficit:   todosLosRegistros.filter(r => r.estadoCumplimiento && r.estadoCumplimiento.includes('Déficit')).length,
      depurado:  todosLosRegistros.filter(r => r.numMarcaciones >= 5).length
    };
    Object.entries(tabs).forEach(([tab, cnt]) => {
      const el = document.getElementById(`tab-count-${tab}`);
      if (el) el.textContent = cnt;
    });
  }

  // ── Cargar y renderizar datos ────────────────────────────
  async function cargar(params = {}) {
    const tbody = document.getElementById('asistencia-tbody');
    if (tbody) {
      tbody.innerHTML = Array.from({length: 8}, () =>
        '<tr>' + Array.from({length: 19}, () => '<td><div class="skeleton skeleton-row" style="height:14px;width:80%"></div></td>').join('') + '</tr>'
      ).join('');
    }
    try {
      todosLosRegistros = await Api.getAsistencia(params);
      registrosFiltrados = aplicarFiltroTab(todosLosRegistros, filtroTab);
      paginaActual = 1;
      renderTabla();
      await cargarResumenIndividual(params);
    } catch (err) {
      console.error('[Asistencia]', err);
      Toast.error('Error al cargar datos: ' + err.message);
    }
  }

  // ── Tabla Resumen Individual (4 Pilares) ──────────────────
  async function cargarResumenIndividual(params = {}) {
    const cont = document.getElementById('resumen-individual-container');
    if (!cont) return;

    try {
      const res = await Api.getResumenIndividual(params);
      if (!res || res.totalEsperadoIngreso === 0) {
        cont.style.display = 'none';
        return;
      }

      cont.style.display = 'block';
      cont.innerHTML = `
        <div style="max-width: 520px; background: #ffffff; border: 1px solid #000000; border-radius: 4px; box-shadow: 0 2px 6px rgba(0,0,0,0.06); overflow: hidden; font-size: 0.82rem; margin-top: 1rem;">
          <!-- Header Navy Blue #1F4E78 -->
          <div style="background-color: #1F4E78; color: #ffffff; font-weight: 700; text-align: center; padding: 0.55rem 1rem; font-size: 0.88rem; letter-spacing: 0.5px; border-bottom: 1px solid #000000;">
            RESUMEN INDIVIDUAL
          </div>

          <!-- Filas de datos generales -->
          <table style="width: 100%; border-collapse: collapse;">
            <tbody>
              <tr style="border-bottom: 1px solid #000000;">
                <td colspan="2" style="padding: 0.4rem 0.75rem; font-weight: 600; color: #0D2B4E; border-right: 1px solid #000000;">Días con registro en el período:</td>
                <td style="padding: 0.4rem 0.75rem; text-align: center; font-weight: 600; width: 110px;">${res.diasConRegistro}</td>
              </tr>
              <tr style="border-bottom: 1px solid #000000;">
                <td colspan="2" style="padding: 0.4rem 0.75rem; font-weight: 600; color: #0D2B4E; border-right: 1px solid #000000;">Tardanzas de ingreso:</td>
                <td style="padding: 0.4rem 0.75rem; text-align: center; font-weight: 600; width: 110px;">${res.tardanzasIngresoDias}</td>
              </tr>
              <tr style="border-bottom: 1px solid #000000;">
                <td colspan="2" style="padding: 0.4rem 0.75rem; font-weight: 600; color: #0D2B4E; border-right: 1px solid #000000;">Total minutos de tardanza:</td>
                <td style="padding: 0.4rem 0.75rem; text-align: center; font-weight: 600; width: 110px;">${res.totalMinutosTardanza}</td>
              </tr>

              <!-- Sub-encabezado de 3 columnas para la matriz de 4 pilares -->
              <tr style="border-bottom: 1px solid #000000; font-size: 0.76rem;">
                <th style="padding: 0.4rem 0.75rem; text-align: left; background: #F8FAFC; border-right: 1px solid #000000;"></th>
                <th style="padding: 0.4rem 0.5rem; text-align: center; background: #D6E8F7; color: #0D2B4E; font-weight: 700; border-right: 1px solid #000000; width: 140px;">TOTAL DE REGISTROS</th>
                <th style="padding: 0.4rem 0.5rem; text-align: center; background: #FADBD8; color: #922B21; font-weight: 700; width: 110px;">NO REGISTRA</th>
              </tr>

              <!-- 4 Pilares -->
              <tr style="border-bottom: 1px solid #000000;">
                <td style="padding: 0.35rem 0.75rem; font-weight: 500; border-right: 1px solid #000000;">1. Ingreso</td>
                <td style="padding: 0.35rem 0.5rem; text-align: center; border-right: 1px solid #000000;">${res.totalEsperadoIngreso}</td>
                <td style="padding: 0.35rem 0.5rem; text-align: center; font-weight: 700; background: #FADBD8; color: #922B21;">${res.noRegistraIngreso}</td>
              </tr>
              <tr style="border-bottom: 1px solid #000000;">
                <td style="padding: 0.35rem 0.75rem; font-weight: 500; border-right: 1px solid #000000;">2. Salida a refrigerio</td>
                <td style="padding: 0.35rem 0.5rem; text-align: center; border-right: 1px solid #000000;">${res.totalEsperadoSalidaRefrigerio}</td>
                <td style="padding: 0.35rem 0.5rem; text-align: center; font-weight: 700; background: #FADBD8; color: #922B21;">${res.noRegistraSalidaRefrigerio}</td>
              </tr>
              <tr style="border-bottom: 1px solid #000000;">
                <td style="padding: 0.35rem 0.75rem; font-weight: 500; border-right: 1px solid #000000;">3. Retorno de refrigerio</td>
                <td style="padding: 0.35rem 0.5rem; text-align: center; border-right: 1px solid #000000;">${res.totalEsperadoRetornoRefrigerio}</td>
                <td style="padding: 0.35rem 0.5rem; text-align: center; font-weight: 700; background: #FADBD8; color: #922B21;">${res.noRegistraRetornoRefrigerio}</td>
              </tr>
              <tr style="border-bottom: 1px solid #000000;">
                <td style="padding: 0.35rem 0.75rem; font-weight: 500; border-right: 1px solid #000000;">4. Salida final</td>
                <td style="padding: 0.35rem 0.5rem; text-align: center; border-right: 1px solid #000000;">${res.totalEsperadoSalidaFinal}</td>
                <td style="padding: 0.35rem 0.5rem; text-align: center; font-weight: 700; background: #FADBD8; color: #922B21;">${res.noRegistraSalidaFinal}</td>
              </tr>

              <!-- Fila de cierre -->
              <tr>
                <td colspan="2" style="padding: 0.4rem 0.75rem; font-weight: 600; color: #0D2B4E; border-right: 1px solid #000000;">Días con marcaciones fuera de rango:</td>
                <td style="padding: 0.4rem 0.75rem; text-align: center; font-weight: 600;">${res.diasConMarcacionesFueraDeRango}</td>
              </tr>
            </tbody>
          </table>
        </div>
      `;
    } catch (e) {
      console.warn('[ResumenIndividual]', e);
      cont.style.display = 'none';
    }
  }

  // ── Cambio de pestaña ────────────────────────────────────
  function cambiarTab(tab) {
    filtroTab = tab;
    paginaActual = 1;
    document.querySelectorAll('.tab-btn').forEach(b => b.classList.toggle('active', b.dataset.tab === tab));
    registrosFiltrados = aplicarFiltroTab(todosLosRegistros, tab);
    renderTabla();
  }

  // ── Paginación ───────────────────────────────────────────
  function irPagina(n) {
    paginaActual = n;
    renderTabla();
    document.getElementById('vista-asistencia')?.scrollTo({ top: 0 });
  }

  // ── Ordenamiento por columna ─────────────────────────────
  function ordenarPor(col) {
    if (sortCol === col) {
      sortDir = sortDir === 'asc' ? 'desc' : 'asc';
    } else {
      sortCol = col;
      sortDir = 'asc';
    }
    document.querySelectorAll('th[data-col]').forEach(th => {
      th.classList.remove('sorted-asc', 'sorted-desc');
      if (th.dataset.col === col) th.classList.add('sorted-' + sortDir);
    });
    renderTabla();
  }

  // ── Modal del Doctor ─────────────────────────────────────
  function abrirModal(id) {
    const reg = todosLosRegistros.find(r => r.id === id);
    if (!reg) return;

    document.getElementById('modal-empid').textContent  = reg.empId || '—';
    document.getElementById('modal-nombre').textContent = `${reg.nombres || ''} ${reg.apellidos || ''}`.trim() || `EMP-${reg.empId}`;
    document.getElementById('modal-depto').textContent  = reg.departamento || '—';
    document.getElementById('modal-fecha').textContent  = reg.fecha || '—';
    document.getElementById('modal-marcaciones').textContent = reg.numMarcaciones ?? '0';
    document.getElementById('modal-horas-crudas').textContent = reg.horasCrudas || '—';
    document.getElementById('modal-condicion').innerHTML  = badgeCondicion(reg.condicionIngreso);
    document.getElementById('modal-tardanza').textContent = `${reg.minutosTardanza || 0} min`;
    document.getElementById('modal-jornada').textContent  = reg.jornadaEfectiva || '—';
    document.getElementById('modal-balance').innerHTML    = fmtBalance(reg.balanceJornada);
    document.getElementById('modal-estado').innerHTML     = badgeEstado(reg.estadoCumplimiento);

    // Si ya hay una justificación previa
    const justActual = document.getElementById('modal-just-actual');
    if (reg.estadoJustificacion && reg.estadoJustificacion !== 'NINGUNO') {
      justActual.innerHTML = `<div style="background:var(--morado-bg);border-radius:8px;padding:.6rem .8rem;font-size:.78rem;color:var(--morado)">
        <strong>Justificación vigente:</strong> ${reg.estadoJustificacion.replace(/_/g,' ')}<br>
        <span style="opacity:.8">${reg.observacionDoctor || ''}</span>
      </div>`;
    } else {
      justActual.innerHTML = '';
    }

    // Limpiar formulario
    document.getElementById('modal-tipo-just').value = '';
    document.getElementById('modal-observacion').value = '';

    document.getElementById('modal-registro-id').value = id;
    document.getElementById('modal-overlay').classList.add('open');
  }

  function cerrarModal() {
    document.getElementById('modal-overlay').classList.remove('open');
  }

  async function guardarJustificacion() {
    const id   = Number(document.getElementById('modal-registro-id').value);
    const tipo = document.getElementById('modal-tipo-just').value;
    const obs  = document.getElementById('modal-observacion').value.trim();

    if (!tipo) { Toast.warn('Seleccione un tipo de justificación'); return; }

    const btn = document.getElementById('btn-modal-save');
    btn.classList.add('saving');
    btn.innerHTML = '<span class="spinner"></span> Guardando…';

    try {
      const reg = await Api.justificarRegistro(id, tipo, obs || `${tipo.replace(/_/g,' ')} aplicado por el Doctor`);
      // Actualizar en el dataset local
      const idx = todosLosRegistros.findIndex(r => r.id === id);
      if (idx >= 0) {
        todosLosRegistros[idx] = { ...todosLosRegistros[idx], ...reg };
      }
      registrosFiltrados = aplicarFiltroTab(todosLosRegistros, filtroTab);
      renderTabla();
      cerrarModal();
      Toast.ok(`Justificación ${tipo.replace(/_/g,' ')} aplicada correctamente`);
    } catch (err) {
      Toast.error('Error al guardar: ' + err.message);
    } finally {
      btn.classList.remove('saving');
      btn.innerHTML = `
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>
        Aplicar Justificación`;
    }
  }

  // ── Modal Edición Manual de Asistencia (ADMIN) ──────────
  function abrirModalEditar(id) {
    const reg = todosLosRegistros.find(r => r.id === id);
    if (!reg) return;

    document.getElementById('edit-asist-id').value = id;
    document.getElementById('edit-asist-titulo').textContent = `Editar Registro #${id} — ${reg.nombres || ''} ${reg.apellidos || ''}`;
    document.getElementById('edit-asist-empid').value = reg.empId || '';
    document.getElementById('edit-asist-fecha').value = reg.fecha || '';
    document.getElementById('edit-asist-entrada').value = reg.horaEntrada || '';
    document.getElementById('edit-asist-inialm').value = reg.inicioAlmuerzo || '';
    document.getElementById('edit-asist-finalm').value = reg.finAlmuerzo || '';
    document.getElementById('edit-asist-salida').value = reg.horaSalida || '';
    document.getElementById('edit-asist-crudas').value = reg.horasCrudas || '';
    document.getElementById('edit-asist-obs').value = reg.observacionDoctor || '';

    document.getElementById('modal-editar-asistencia').style.display = 'flex';
  }

  function cerrarModalEditar() {
    const m = document.getElementById('modal-editar-asistencia');
    if (m) m.style.display = 'none';
  }

  async function guardarEdicion() {
    const id = Number(document.getElementById('edit-asist-id').value);
    const entrada = document.getElementById('edit-asist-entrada').value.trim();
    const iniAlm = document.getElementById('edit-asist-inialm').value.trim();
    const finAlm = document.getElementById('edit-asist-finalm').value.trim();
    const salida = document.getElementById('edit-asist-salida').value.trim();
    const crudas = document.getElementById('edit-asist-crudas').value.trim();
    const obs = document.getElementById('edit-asist-obs').value.trim();

    const payload = {
      horaEntrada: entrada ? (entrada.length === 5 ? entrada + ':00' : entrada) : null,
      inicioAlmuerzo: iniAlm ? (iniAlm.length === 5 ? iniAlm + ':00' : iniAlm) : null,
      finAlmuerzo: finAlm ? (finAlm.length === 5 ? finAlm + ':00' : finAlm) : null,
      horaSalida: salida ? (salida.length === 5 ? salida + ':00' : salida) : null,
      horasCrudas: crudas || null,
      observacionDoctor: obs || null,
      recalcularAutomatico: true
    };

    try {
      const regActualizado = await Api.editarAsistencia(id, payload);
      const idx = todosLosRegistros.findIndex(r => r.id === id);
      if (idx >= 0) {
        todosLosRegistros[idx] = { ...todosLosRegistros[idx], ...regActualizado };
      }
      registrosFiltrados = aplicarFiltroTab(todosLosRegistros, filtroTab);
      renderTabla();
      cerrarModalEditar();
      Toast.ok(`Registro #${id} actualizado y recalculado correctamente.`);
    } catch (err) {
      Toast.error('Error al editar registro: ' + err.message);
    }
  }

  async function eliminar(id) {
    if (!confirm(`¿Está seguro de eliminar el registro de asistencia #${id}? Esta acción no se puede deshacer.`)) {
      return;
    }
    try {
      await Api.eliminarAsistencia(id);
      todosLosRegistros = todosLosRegistros.filter(r => r.id !== id);
      registrosFiltrados = aplicarFiltroTab(todosLosRegistros, filtroTab);
      renderTabla();
      Toast.ok(`Registro #${id} eliminado exitosamente.`);
    } catch (err) {
      Toast.error('Error al eliminar registro: ' + err.message);
    }
  }

  return {
    cargar,
    cambiarTab,
    irPagina,
    ordenarPor,
    abrirModal,
    cerrarModal,
    guardarJustificacion,
    abrirModalEditar,
    cerrarModalEditar,
    guardarEdicion,
    eliminar
  };
})();

