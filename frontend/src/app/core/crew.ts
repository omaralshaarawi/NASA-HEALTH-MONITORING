export interface CrewMember {
  id: string;
  name: string;
  role: string;
}

export const CREW: CrewMember[] = [
  { id: 'ast_nova', name: 'Nova', role: 'Commander' },
  { id: 'ast_kai', name: 'Kai', role: 'Flight engineer' },
  { id: 'ast_lin', name: 'Lin', role: 'Mission specialist' },
];