import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/** Root used only when running alone; inside the shell the shell's layout wraps the routes. */
@Component({
  selector: 'cd-root',
  imports: [RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p class="kicker standalone">mfe-proposals · standalone</p>
    <main><router-outlet /></main>
  `,
  styles: `
    .standalone {
      padding: 16px var(--gutter) 0;
    }
    main {
      padding: 16px var(--gutter) 40px;
      max-width: var(--max);
      margin: 0 auto;
    }
  `,
})
export class App {}
