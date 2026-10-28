export const STATUS_TABS = ['ACTIVE', 'ON_LEAVE', 'RETIRED'];
export const roleTone = (code) =>
  code === 'CHIEF' ? 'red' : code === 'LEADER' ? 'blue' : code === 'EMPLOYEE' ? 'gray' : 'green';
