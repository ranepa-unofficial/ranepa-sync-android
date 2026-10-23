package com.qteb.ranepa.sync.lk

/** Скрипты для страницы ЛК — те же, что в iOS-версии, с мостом Android вместо WebKit. */
internal object LkScripts {
    /** Перехватывает запрос расписания, который делает сама страница. */
    const val HOOK = """
(function () {
  if (window.__rtHooked) return;
  window.__rtHooked = true;
  function report(url, headers) {
    try {
      var abs = new URL(url, location.href).href;
      if (abs.indexOf('/n-api/schedule?') === -1) return;
      if (window.RanepaBridge) RanepaBridge.scheduleRequest(JSON.stringify({ url: abs, headers: headers || {} }));
    } catch (e) {}
  }
  var open = XMLHttpRequest.prototype.open;
  var setHeader = XMLHttpRequest.prototype.setRequestHeader;
  var send = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function (method, url) {
    this.__rtUrl = url; this.__rtHeaders = {};
    return open.apply(this, arguments);
  };
  XMLHttpRequest.prototype.setRequestHeader = function (k, v) {
    if (this.__rtHeaders) this.__rtHeaders[k] = v;
    return setHeader.apply(this, arguments);
  };
  XMLHttpRequest.prototype.send = function () {
    if (this.__rtUrl) report(this.__rtUrl, this.__rtHeaders);
    return send.apply(this, arguments);
  };
  var origFetch = window.fetch;
  if (origFetch) window.fetch = function (input, init) {
    try {
      var h = {};
      if (init && init.headers) new Headers(init.headers).forEach(function (v, k) { h[k] = v; });
      report(typeof input === 'string' ? input : input.url, h);
    } catch (e) {}
    return origFetch.apply(this, arguments);
  };
})();
"""

    /**
     * Ставит «Запомнить меня» на форме входа: с ней сессия ЛК живёт 30 дней,
     * без неё — сутки. Нажимаем сами, как пользователь, — так Vue узнаёт
     * о галочке. Один раз на форму: если её сняли, не возвращаем.
     */
    const val REMEMBER_ME = """
(function () {
  if (window.__rtRemember) return;
  window.__rtRemember = true;
  function apply() {
    document.querySelectorAll('label').forEach(function (label) {
      if (label.textContent.indexOf('Запомнить') === -1) return;
      var input = document.getElementById(label.htmlFor);
      if (!input || input.type !== 'checkbox' || input.dataset.rtRemember) return;
      input.dataset.rtRemember = '1';
      if (!input.checked) input.click();
    });
  }
  apply();
  new MutationObserver(apply).observe(document.documentElement, { childList: true, subtree: true });
})();
"""

    /**
     * Загрузка недель расписания, профиля, плана и фото в уже авторизованной
     * странице. Аргументы — в объекте ARGS, ответ уходит в мост строкой JSON.
     */
    fun fetch(id: String, args: String) = """
(async function () {
  const { urls, headers, stopAfterEmpty, minWeeks, groupsUrl, profileUrl, photoUrl, withProfile, withPhoto, withPlan } = $args;
  const auth = { authorization: 'Bearer ' + localStorage.getItem('access_token') };
  async function get(url) {
    const r = await fetch(url, { headers: Object.assign({}, headers, auth) });
    const ct = r.headers.get('content-type') || '';
    if (r.status === 401) throw new Error('auth');
    if (!r.ok || ct.indexOf('json') === -1) {
      throw new Error('http ' + r.status + ' ' + (r.headers.get('retry-after') || ''));
    }
    return r.json();
  }
  try {
    const days = [];
    let empty = 0;
    for (let i = 0; i < urls.length; i++) {
      const week = (await get(urls[i])).days || [];
      days.push(...week);
      const count = week.reduce((n, day) => n + ((day.discs || []).length), 0);
      empty = count ? 0 : empty + 1;
      if (stopAfterEmpty && i + 1 >= minWeeks && empty >= stopAfterEmpty) break;
    }
    // Профиль — необязательная часть: ошибка не должна ломать расписание.
    const out = { days: days };
    if (withProfile) try {
      const sg = ((await get(groupsUrl)).items || [])[0] || {};
      out.group = sg.academic_group;
      out.program = sg.edu_program_title;
      out.uid = sg.uid_student;
    } catch (e) {}
    // План обучения: все семестры, формы контроля, даты сессий.
    if (withPlan && out.uid) try {
      const base = groupsUrl.replace('manual/student-groups', '');
      const plan = await get(base + 'education/plan?uid_student=' + out.uid);
      const sessions = await get(base + 'education/plan/sessions?uid_student=' + out.uid);
      const refs = (await get(base + 'manual/references?name[]=Semestry&name[]=VidyAttestacii')).result || {};
      // Справочник — вложенный объект; нужен словарь «uid → название».
      const flat = x => {
        if (!x || typeof x !== 'object') return null;
        const v = Object.values(x);
        if (v.length && v.every(s => typeof s === 'string')) return x;
        for (const y of v) { const r = flat(y); if (r) return r; }
        return null;
      };
      const sem = flat(refs.Semestry) || {}, vid = flat(refs.VidyAttestacii) || {};
      out.plan = {
        items: (plan.items || []).map(i => ({
          name: i.discipline, control: vid[i.uid_control] || null, hours: i.hours || 0, ze: i.ze || 0,
          course: i.course || 0, semester: sem[i.uid_semestr] || '', elective: !!i.elective,
          practice: !!i.practice, courseWork: !!i.course_work, facultative: !!i.facultative,
          selected: i.is_selected !== false })),
        sessions: (sessions.items || []).map(s => ({ course: s.cours, semester: s.semestr, start: s.start_date, end: s.end_date })),
      };
    } catch (e) {}
    delete out.uid;
    if (withProfile) try {
      const p = (await get(profileUrl)).item || {};
      out.name = [p.last_name, p.first_name, p.middle_name].filter(Boolean).join(' ');
    } catch (e) {}
    if (withPhoto) {
      try { out.photo = (await get(photoUrl)).result || ''; } catch (e) {}
    }
    // Только срок refresh-токена (поле exp), не сам токен.
    try {
      const part = (localStorage.getItem('refresh_token') || '').split('.')[1];
      const exp = JSON.parse(atob(part.replace(/-/g, '+').replace(/_/g, '/'))).exp;
      if (exp) out.sessionUntil = exp;
    } catch (e) {}
    return JSON.stringify(out);
  } catch (e) {
    let error = String(e.message || e);
    // Если ЛК не отдаёт группы (5xx), страница строит запрос расписания
    // без них и получает 400. Это сбой сайта, а не неверный запрос.
    if (error.indexOf('http 400') === 0) try { await get(groupsUrl); } catch (g) {
      if (/^http 5/.test(String(g.message))) error = String(g.message);
    }
    return JSON.stringify({ error: error });
  }
})().then(function (r) { RanepaBridge.result('$id', r); },
         function (e) { RanepaBridge.result('$id', JSON.stringify({ error: String(e) })); });
"""
}
