import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

/** List of all clients. A placeholder until the clients fields are decided. */
@Component({
  imports: [MatCardModule],
  selector: 'app-client-list',
  styleUrl: './client-list.scss',
  templateUrl: './client-list.html',
})
export class ClientList {}
