import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Root component of the creditsAI single-page application.
 * It only hosts the router outlet; the layout shell and features are added in later steps.
 */
@Component({
  imports: [RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {}
