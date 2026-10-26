import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

/** List of all credits. A placeholder until the credits fields are decided. */
@Component({
  imports: [MatCardModule],
  selector: 'app-credit-list',
  styleUrl: './credit-list.scss',
  templateUrl: './credit-list.html',
})
export class CreditList {}
