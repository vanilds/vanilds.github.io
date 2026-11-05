import { userFromToken } from './auth';

function token(payload: object): string {
  // like a real JWT: UTF-8 bytes, then base64url
  const b64 = (o: object) =>
    btoa(String.fromCharCode(...new TextEncoder().encode(JSON.stringify(o))))
      .replace(/=+$/, '')
      .replace(/\+/g, '-')
      .replace(/\//g, '_');
  return `${b64({ alg: 'RS256' })}.${b64(payload)}.signature`;
}

describe('userFromToken', () => {
  it('reads id, name and the CreditDesk realm roles', () => {
    const user = userFromToken(
      token({
        sub: 'a1',
        preferred_username: 'marcos',
        name: 'Marcos Gerente',
        realm_access: { roles: ['MANAGER', 'offline_access', 'uma_authorization'] },
      }),
    );
    expect(user).toEqual({
      id: 'a1',
      username: 'marcos',
      name: 'Marcos Gerente',
      roles: ['MANAGER'],
    });
  });

  it('decodes UTF-8 names (base64url)', () => {
    expect(userFromToken(token({ sub: 'x', name: 'João Ação' }))?.name).toBe('João Ação');
  });

  it('returns null for garbage', () => {
    expect(userFromToken('not-a-jwt')).toBeNull();
  });
});
