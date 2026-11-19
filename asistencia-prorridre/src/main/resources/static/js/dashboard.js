// ============================================================
// PRORRIDRE — dashboard.js
// Motor de analítica: KPIs + 4 gráficos Chart.js
// ============================================================
const Dashboard = (() => {
  // Referencias a instancias de Chart para destruirlas antes de recrear
  const charts = {};

  // Colores corporativos PRORRIDRE
  const C = {
    azul:     '#2471a3',
    azulClaro:'#5dade2',
    verde:    '#1a7f37',
    rojo:     '#c0392b',
    ambar:    '#d08b00',
    morado:   '#7c3aed',
    gris:     '#94a3b8',
    // Paleta de departamentos (hasta 12)
    depto: [
      '#2471a3','#1a7f37','#c0392b','#d08b00','#7c3aed',
      '#0369a1','#166534','#9a3412','#92400e','#5b21b6',
      '#0e7490','#15803d'
    ]
  };

  let kpiData = null; // Último payload del dashboard

  // ── Helpers de formateo ──────────────────────────────────
  function fmt(n, dec = 0) {
    return n == null ? '—' : Number(n).toLocaleString('es-PE', { minimumFractionDigits: dec, maximumFractionDigits: dec });
  }
  function pct(n) { return n == null ? '—' : Number(n).toFixed(1) + '%'; }
  function horas(h) { return h == null ? '—' : Number(h).toFixed(1) + ' h'; }

  // ── Renderizar las 6 Tarjetas KPI ────────────────────────
  function renderKpis(d) {
    const total = d.totalRegistrosEvaluados || 0;
    const puntual = d.pctATiempo || 0;
    const tarde = d.pctTardanza || 0;
    const minTarde = d.totalMinutosTardanza || 0;
    const hEfec = d.totalHorasEfectivas || 0;
    const hExtra = d.totalHorasExtra || 0;
    const hDef = d.totalHorasDeficit || 0;
    const incompletos = d.countIncompleto || 0;

    document.getElementById('kpi-registros').innerHTML = `
      <div class="kpi-header">
        <div class="kpi-icon" style="--kpi-color:var(--azul-vivo);--kpi-bg:var(--azul-claro)">
          ${icon('users')}
        </div>
        <span class="kpi-badge info">${fmt(d.totalEmpleadosActivos)} activos</span>
      </div>
      <div class="kpi-label">Registros evaluados</div>
      <div class="kpi-value">${fmt(total)}</div>
      <div class="kpi-sub">${d.totalDiasHabilesRango || 0} días hábiles · ${d.fechaInicio || ''} → ${d.fechaFin || ''}</div>`;

    document.getElementById('kpi-puntualidad').innerHTML = `
      <div class="kpi-header">
        <div class="kpi-icon" style="--kpi-color:var(--verde);--kpi-bg:var(--verde-bg)">
          ${icon('clock')}
        </div>
        <span class="kpi-badge up">▲ ${pct(puntual)}</span>
      </div>
      <div class="kpi-label">Índice de Puntualidad</div>
      <div class="kpi-value" style="color:var(--verde)">${pct(puntual)}</div>
      <div class="kpi-sub">A Tiempo · Tardanza: ${pct(tarde)} (${fmt(d.countTardanza)})</div>`;

    document.getElementById('kpi-tardanza').innerHTML = `
      <div class="kpi-header">
        <div class="kpi-icon" style="--kpi-color:var(--ambar);--kpi-bg:var(--ambar-bg)">
          ${icon('alert')}
        </div>
        <span class="kpi-badge warn">${fmt(d.countTardanza)} empleados</span>
      </div>
      <div class="kpi-label">Minutos de Tardanza</div>
      <div class="kpi-value" style="color:var(--ambar)">${fmt(minTarde)}</div>
      <div class="kpi-sub">Prom. ${fmt(d.promedioMinutosTardanza, 1)} min/empleado tardío</div>`;

    document.getElementById('kpi-horas').innerHTML = `
      <div class="kpi-header">
        <div class="kpi-icon" style="--kpi-color:var(--celeste);--kpi-bg:var(--celeste-bg)">
          ${icon('calendar')}
        </div>
        <span class="kpi-badge info">≈ ${horas(hEfec / Math.max(total, 1))} /día</span>
      </div>
      <div class="kpi-label">Horas Efectivas Totales</div>
      <div class="kpi-value">${horas(hEfec)}</div>
      <div class="kpi-sub">${fmt(d.countReglamentaria)} jornadas reglamentarias · ${fmt(d.countExcedente)} excedentes</div>`;

    const balancePos = hExtra > 0;
    document.getElementById('kpi-balance').innerHTML = `
      <div class="kpi-header">
        <div class="kpi-icon" style="--kpi-color:${hDef > hExtra ? 'var(--rojo)' : 'var(--verde)'};--kpi-bg:${hDef > hExtra ? 'var(--rojo-bg)' : 'var(--verde-bg)'}">
          ${icon('balance')}
        </div>
        ${hDef > hExtra
          ? `<span class="kpi-badge down">▼ ${horas(hDef - hExtra)}</span>`
          : `<span class="kpi-badge up">▲ ${horas(hExtra - hDef)}</span>`}
      </div>
      <div class="kpi-label">Balance Neto de Horas</div>
      <div class="kpi-value" style="color:${hDef > hExtra ? 'var(--rojo)' : 'var(--verde)'}">
        ${hDef > hExtra ? '-' : '+'}${horas(Math.abs(hExtra - hDef))}
      </div>
      <div class="kpi-sub">+Excedente ${horas(hExtra)} · −Déficit ${horas(hDef)}</div>`;

    document.getElementById('kpi-omisiones').innerHTML = `
      <div class="kpi-header">
        <div class="kpi-icon" style="--kpi-color:var(--rojo);--kpi-bg:var(--rojo-bg)">
          ${icon('warning')}
        </div>
        <span class="kpi-badge down">${fmt(d.diasHombreFalta)} faltas</span>
      </div>
      <div class="kpi-label">Alertas de Omisión</div>
      <div class="kpi-value" style="color:var(--rojo)">${fmt(incompletos)}</div>
      <div class="kpi-sub">Registros incompletos + Días-Hombre falta: ${fmt(d.diasHombreFalta)}</div>`;
  }

  // ── Crear / Actualizar gráficos ───────────────────────────
  function renderGraficos(d) {
    // Gráfico 1: Dona — Distribución de Cumplimiento
    _destroyChart('graficoDona');
    const donaCfg = {
      type: 'doughnut',
      data: {
        labels: ['Jornada Reglamentaria', 'Excedente de Jornada', 'Déficit de Jornada', 'Incompleto / Omisión'],
        datasets: [{
          data: [d.countReglamentaria, d.countExcedente, d.countDeficit, d.countIncompleto],
          backgroundColor: [C.verde, C.azul, C.ambar, C.rojo],
          borderWidth: 2,
          borderColor: '#fff',
          hoverOffset: 6
        }]
      },
      options: {
        responsive: true, maintainAspectRatio: false,
        cutout: '65%',
        plugins: {
          legend: { position: 'bottom', labels: { font: { size: 11 }, padding: 12, boxWidth: 12 } },
          tooltip: {
            callbacks: {
              label: (c) => {
                const total = c.dataset.data.reduce((a, b) => a + b, 0);
                return ` ${c.label}: ${c.raw} (${((c.raw / total) * 100).toFixed(1)}%)`;
              }
            }
          }
        }
      }
    };
    charts['graficoDona'] = new Chart(document.getElementById('graficoDona'), donaCfg);

    // Gráfico 2: Barras Horizontales — Top Departamentos por Tardanza + Déficit
    _destroyChart('graficoDeptos');
    const ranking = (d.rankingDepartamentos || []).slice(0, 10);
    const labelsD = ranking.map(r => r.departamento.length > 22 ? r.departamento.substring(0, 22) + '…' : r.departamento);
    charts['graficoDeptos'] = new Chart(document.getElementById('graficoDeptos'), {
      type: 'bar',
      data: {
        labels: labelsD,
        datasets: [
          {
            label: 'Tardanzas (registros)',
            data: ranking.map(r => r.tardanzas),
            backgroundColor: C.ambar + 'cc',
            borderRadius: 4
          },
          {
            label: 'Incompletos',
            data: ranking.map(r => r.incompletos),
            backgroundColor: C.rojo + 'cc',
            borderRadius: 4
          },
          {
            label: 'Faltas (Días-Hombre)',
            data: ranking.map(r => r.faltas),
            backgroundColor: '#94a3b8bb',
            borderRadius: 4
          }
        ]
      },
      options: {
        responsive: true, maintainAspectRatio: false,
        indexAxis: 'y',
        plugins: {
          legend: { position: 'top', labels: { font: { size: 11 }, boxWidth: 12, padding: 10 } },
          tooltip: { mode: 'index', intersect: false }
        },
        scales: {
          x: {
            stacked: false,
            grid: { color: '#e9eef4' },
            ticks: { font: { size: 11 } }
          },
          y: {
            ticks: { font: { size: 10 } },
            grid: { display: false }
          }
        }
      }
    });

    // Gráfico 3: Línea/Área — Evolución diaria de puntualidad (ausencias por fecha)
    _destroyChart('graficoLinea');
    const ausenciasPorFecha = {};
    (d.detalleAusencias || []).forEach(a => {
      ausenciasPorFecha[a.fecha] = (ausenciasPorFecha[a.fecha] || 0) + 1;
    });
    const fechasOrdenadas = Object.keys(ausenciasPorFecha).sort();
    charts['graficoLinea'] = new Chart(document.getElementById('graficoLinea'), {
      type: 'line',
      data: {
        labels: fechasOrdenadas.map(f => {
          const p = f.split('-');
          return `${p[2]}/${p[1]}`;
        }),
        datasets: [{
          label: 'Faltas (Días-Hombre)',
          data: fechasOrdenadas.map(f => ausenciasPorFecha[f]),
          borderColor: C.rojo,
          backgroundColor: C.rojo + '18',
          fill: true,
          tension: 0.3,
          pointRadius: 3,
          pointBackgroundColor: C.rojo
        }]
      },
      options: {
        responsive: true, maintainAspectRatio: false,
        plugins: {
          legend: { display: true, labels: { font: { size: 11 } } }
        },
        scales: {
          x: { grid: { color: '#e9eef4' }, ticks: { font: { size: 10 }, maxTicksLimit: 12 } },
          y: { beginAtZero: true, grid: { color: '#e9eef4' }, ticks: { font: { size: 11 } } }
        }
      }
    });

    // Gráfico 4: Barras Agrupadas — Horas Extra vs Déficit por Área
    _destroyChart('graficoBalance');
    const topRanking = (d.rankingDepartamentos || []).slice(0, 8);
    charts['graficoBalance'] = new Chart(document.getElementById('graficoBalance'), {
      type: 'bar',
      data: {
        labels: topRanking.map(r => r.departamento.length > 18 ? r.departamento.substring(0, 18) + '…' : r.departamento),
        datasets: [
          {
            label: '+Horas Efectivas/día promedio',
            data: topRanking.map(r => Number((r.promedioHorasDia || 0).toFixed(2))),
            backgroundColor: C.verde + 'cc',
            borderRadius: 4
          },
          {
            label: 'Tardanzas (minutos)',
            data: topRanking.map(r => Number(((r.totalMinutosTardanza || 0) / 60).toFixed(2))),
            backgroundColor: C.rojo + 'cc',
            borderRadius: 4
          }
        ]
      },
      options: {
        responsive: true, maintainAspectRatio: false,
        plugins: {
          legend: { position: 'top', labels: { font: { size: 11 }, boxWidth: 12 } },
          tooltip: {
            callbacks: {
              label: (c) => ` ${c.dataset.label}: ${c.raw} h`
            }
          }
        },
        scales: {
          x: { grid: { display: false }, ticks: { font: { size: 10 } } },
          y: {
            beginAtZero: true,
            grid: { color: '#e9eef4' },
            ticks: { font: { size: 11 } },
            title: { display: true, text: 'Horas', font: { size: 10 } }
          }
        }
      }
    });
  }

  function _destroyChart(id) {
    if (charts[id]) { charts[id].destroy(); delete charts[id]; }
  }

  // ── Cargar y renderizar todo ─────────────────────────────
  async function cargar(params = {}) {
    _mostrarSkeleton();
    try {
      const d = await Api.getDashboard(params);
      kpiData = d;
      renderKpis(d);
      renderGraficos(d);

      // Control de alerta de empleados desconocidos
      const banner = document.getElementById('banner-desconocidos');
      if (banner) {
        if (d.countEmpleadosDesconocidos > 0) {
          banner.style.display = 'flex';
          const tit = document.getElementById('banner-desconocidos-titulo');
          const desc = document.getElementById('banner-desconocidos-desc');
          if (tit) tit.textContent = `⚠️ ${d.countEmpleadosDesconocidos} Empleados Desconocidos Detectados:`;
          if (desc) desc.textContent = `Se detectaron ${d.countEmpleadosDesconocidos} personas marcando sin nombres registrados. Haz clic en "Sincronizar Maestros" para actualizar la nómina institucional.`;
        } else {
          banner.style.display = 'none';
        }
      }

      Toast.ok('Dashboard actualizado');
    } catch (err) {
      console.error('[Dashboard]', err);
      Toast.error('Error al cargar KPIs: ' + err.message);
    }
  }

  function _mostrarSkeleton() {
    ['kpi-registros','kpi-puntualidad','kpi-tardanza','kpi-horas','kpi-balance','kpi-omisiones'].forEach(id => {
      const el = document.getElementById(id);
      if (el) el.innerHTML = `
        <div class="skeleton skeleton-text" style="width:60%"></div>
        <div class="skeleton skeleton-num"></div>
        <div class="skeleton skeleton-text" style="width:80%"></div>`;
    });
  }

  return { cargar, getData: () => kpiData };
})();

// ── Iconos SVG inline ────────────────────────────────────────
function icon(name) {
  const icons = {
    users:    `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/></svg>`,
    clock:    `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>`,
    alert:    `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>`,
    calendar: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>`,
    balance:  `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="2" x2="12" y2="22"/><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/></svg>`,
    warning:  `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>`
  };
  return icons[name] || '';
}

