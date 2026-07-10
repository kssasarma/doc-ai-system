import axios from 'axios';
import type { Group, GroupMember } from '../types';

const BOT_URL = import.meta.env.VITE_BOT_API_URL ?? 'http://localhost:8082';

function headers(token: string) {
  return { Authorization: `Bearer ${token}` };
}

export async function listGroups(token: string): Promise<Group[]> {
  const { data } = await axios.get<Group[]>(`${BOT_URL}/api/groups`, { headers: headers(token) });
  return data;
}

export async function createGroup(token: string, name: string): Promise<Group> {
  const { data } = await axios.post<Group>(`${BOT_URL}/api/groups`, { name }, { headers: headers(token) });
  return data;
}

export async function deleteGroup(token: string, groupId: string): Promise<void> {
  await axios.delete(`${BOT_URL}/api/groups/${groupId}`, { headers: headers(token) });
}

export async function listGroupMembers(token: string, groupId: string): Promise<GroupMember[]> {
  const { data } = await axios.get<GroupMember[]>(`${BOT_URL}/api/groups/${groupId}/members`, { headers: headers(token) });
  return data;
}

export async function addGroupMember(token: string, groupId: string, userId: string): Promise<GroupMember> {
  const { data } = await axios.post<GroupMember>(
    `${BOT_URL}/api/groups/${groupId}/members`, { userId }, { headers: headers(token) },
  );
  return data;
}

export async function removeGroupMember(token: string, groupId: string, userId: string): Promise<void> {
  await axios.delete(`${BOT_URL}/api/groups/${groupId}/members/${userId}`, { headers: headers(token) });
}
