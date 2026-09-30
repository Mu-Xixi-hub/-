const API = 'http://localhost:8080/api';
const $ = id => document.getElementById(id);
let csrf;
let busy = false;

function message(text, error = false) {
  $('message').textContent = text;
  $('message').classList.toggle('error', error);
}

async function request(path, options = {}) {
  const response = await fetch(API + path, {
    ...options,
    credentials: 'include', // 跨端口请求也携带本用户的 JSESSIONID Cookie。
    cache: 'no-store',
    signal: AbortSignal.timeout(15000)
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(data.message || `请求失败（${response.status}）`);
    error.status = response.status;
    throw error;
  }
  return data;
}

async function loadCsrf() { csrf = await request('/csrf'); }
function showLogin() {
  $('login-form').hidden = false;
  $('profile').hidden = true;
  // 清除旧用户信息，避免会话过期后继续显示缓存内容。
  ['nickname','avatar','user-id','user-name','email','department'].forEach(id => $(id).textContent = '');
  $('title').textContent = '欢迎登录';
  $('subtitle').textContent = '登录后查看你的个人信息。';
}

async function loadProfile() {
  const user = await request('/me');
  $('login-form').hidden = true;
  $('profile').hidden = false;
  $('title').textContent = '我的个人信息';
  $('subtitle').textContent = '欢迎回来，' + user.nickname + '。';
  // 使用 textContent，而不是把用户资料拼入 innerHTML。
  $('nickname').textContent = user.nickname;
  $('avatar').textContent = user.nickname.slice(0, 1);
  $('user-id').textContent = user.id;
  $('user-name').textContent = user.username;
  $('email').textContent = user.email;
  $('department').textContent = user.department;
  message('');
}

async function action(task) {
  if (busy) return;
  busy = true;
  document.querySelectorAll('button').forEach(button => button.disabled = true);
  try { await task(); }
  catch (error) {
    if (error.status === 401) showLogin();
    message(error.name === 'TypeError' || error.name === 'TimeoutError'
      ? '无法连接后端，请确认后端已启动，并使用 localhost:5500 打开前端。' : error.message, true);
  } finally {
    busy = false;
    document.querySelectorAll('button').forEach(button => button.disabled = false);
  }
}

$('login-form').addEventListener('submit', event => {
  event.preventDefault();
  action(async () => {
    message('正在登录…');
    await loadCsrf();
    await request('/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded', [csrf.headerName]: csrf.token },
      body: new URLSearchParams({ username: $('username').value.trim(), password: $('password').value })
    });
    $('password').value = '';
    // 登录后原 CSRF Token 失效，重新获取。
    await loadCsrf();
    await loadProfile();
  });
});

$('refresh-button').addEventListener('click', () => action(loadProfile));
$('logout-button').addEventListener('click', () => action(async () => {
  await loadCsrf();
  await request('/logout', { method: 'POST', headers: { [csrf.headerName]: csrf.token } });
  csrf = undefined;
  showLogin();
  message('已安全退出登录。');
}));

// 同一浏览器的多个标签共享 Cookie，切回页面时重新查询身份。
window.addEventListener('focus', () => { if (!busy) action(loadProfile); });
action(async () => {
  showLogin();
  message('正在检查登录状态…');
  await loadCsrf();
  try { await loadProfile(); }
  catch (error) { if (error.status === 401) message('请输入用户名和密码。'); else throw error; }
});
