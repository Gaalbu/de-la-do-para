import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

interface ProducerWrite {
  slug: string;
  displayName: string;
  originLabel: string;
  description: string;
}

interface ProducerUpdate extends ProducerWrite {
  active: boolean;
}

interface Producer extends ProducerUpdate {
  id: string;
  demonstration: true;
  createdAt: string;
  updatedAt: string;
}

interface ProducerPage {
  content: Producer[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

interface ProducerForm {
  slug: string;
  displayName: string;
  originLabel: string;
  description: string;
  active: boolean;
}

const blankForm = (): ProducerForm => ({
  slug: '',
  displayName: '',
  originLabel: '',
  description: '',
  active: true,
});

@Component({
  selector: 'app-admin',
  imports: [FormsModule, RouterLink],
  template: `
    <section class="admin" aria-labelledby="admin-title">
      <header class="page-header">
        <div>
          <p class="eyebrow">Painel administrativo</p>
          <h1 id="admin-title">Produtores</h1>
          <p>Cadastre e mantenha os perfis de demonstração do catálogo.</p>
        </div>
        <nav aria-label="Administração do catálogo">
          <a routerLink="/admin/products">Produtos</a>
          <a routerLink="/admin/inventory">Estoque</a>
          <a routerLink="/">Voltar à vitrine</a>
        </nav>
      </header>

      <aside class="demo-notice" aria-label="Aviso sobre dados de demonstração">
        <strong>Conteúdo de demonstração</strong>
        <p>
          Use apenas localidade ampla e texto editorial fictício. Não inclua coordenadas, endereço
          ou alegações verificáveis.
        </p>
      </aside>

      <div class="layout">
        <section class="producer-list" aria-labelledby="list-title">
          <div class="section-heading">
            <div>
              <h2 id="list-title">Cadastros</h2>
              <p>{{ totalElements() }} {{ totalElements() === 1 ? 'produtor' : 'produtores' }}</p>
            </div>
            <button type="button" class="secondary" (click)="startCreate()">Novo produtor</button>
          </div>

          @if (loading()) {
            <p role="status">Carregando produtores…</p>
          } @else if (listError()) {
            <p role="alert" class="error">{{ listError() }}</p>
            <button type="button" class="secondary" (click)="loadProducers()">
              Tentar novamente
            </button>
          } @else if (producers().length === 0) {
            <p class="empty">Ainda não há produtores cadastrados.</p>
          } @else {
            <ul class="producer-cards">
              @for (producer of producers(); track producer.id) {
                <li>
                  <article class="producer-card" [class.inactive]="!producer.active">
                    <div class="card-title">
                      <h3>{{ producer.displayName }}</h3>
                      <span class="status" [class.status-inactive]="!producer.active">{{
                        producer.active ? 'Ativo' : 'Inativo'
                      }}</span>
                    </div>
                    <p class="origin">{{ producer.originLabel }}</p>
                    <p>{{ producer.description }}</p>
                    <p class="demo-tag">Demonstração</p>
                    <button type="button" class="text-button" (click)="startEdit(producer)">
                      Editar
                    </button>
                  </article>
                </li>
              }
            </ul>
            <nav class="pagination" aria-label="Paginação de produtores">
              <button
                type="button"
                class="secondary"
                (click)="changePage(page() - 1)"
                [disabled]="page() === 0"
              >
                Anterior
              </button>
              <span>Página {{ page() + 1 }} de {{ totalPages() || 1 }}</span>
              <button
                type="button"
                class="secondary"
                (click)="changePage(page() + 1)"
                [disabled]="page() + 1 >= totalPages()"
              >
                Próxima
              </button>
            </nav>
          }
        </section>

        <section class="editor" aria-labelledby="editor-title">
          <h2 id="editor-title">{{ editing() ? 'Editar produtor' : 'Novo produtor' }}</h2>
          <p class="form-intro">Este cadastro não cria conta de acesso para o produtor.</p>
          <form (ngSubmit)="save()" #producerForm="ngForm" novalidate>
            <label for="slug">Identificador</label>
            <input
              id="slug"
              name="slug"
              [(ngModel)]="form.slug"
              required
              pattern="[a-z0-9]+(?:-[a-z0-9]+)*"
              maxlength="80"
              aria-describedby="slug-help"
            />
            <small id="slug-help">Minúsculas, números e hífens.</small>

            <label for="displayName">Nome de exibição</label>
            <input
              id="displayName"
              name="displayName"
              [(ngModel)]="form.displayName"
              required
              maxlength="160"
            />

            <label for="originLabel">Localidade ampla fictícia</label>
            <input
              id="originLabel"
              name="originLabel"
              [(ngModel)]="form.originLabel"
              required
              maxlength="120"
              aria-describedby="origin-help"
            />
            <small id="origin-help"
              >Rotule como demonstração. Não informe endereço ou coordenadas.</small
            >

            <label for="description">Texto editorial fictício</label>
            <textarea
              id="description"
              name="description"
              [(ngModel)]="form.description"
              required
              maxlength="1000"
              rows="4"
              aria-describedby="description-help"
            ></textarea>
            <small id="description-help"
              >Sem alegações verificáveis; identifique como demonstração.</small
            >

            @if (editing()) {
              <label class="active-control" for="active">
                <input id="active" name="active" type="checkbox" [(ngModel)]="form.active" />
                Produtor ativo
              </label>
              <small>Cadastros inativos permanecem guardados para preservar referências.</small>
            }

            @if (saveError()) {
              <p role="alert" class="error">{{ saveError() }}</p>
            }
            @if (notice()) {
              <p role="status" class="success">{{ notice() }}</p>
            }

            <div class="form-actions">
              <button type="submit" [disabled]="saving() || producerForm.invalid">
                {{ saving() ? 'Salvando…' : editing() ? 'Salvar alterações' : 'Criar produtor' }}
              </button>
              @if (editing()) {
                <button type="button" class="secondary" (click)="startCreate()">
                  Cancelar edição
                </button>
              }
            </div>
          </form>
        </section>
      </div>
    </section>
  `,
  styleUrl: './admin.component.css',
})
export class AdminComponent implements OnInit {
  private readonly http = inject(HttpClient);
  readonly producers = signal<Producer[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly listError = signal<string | null>(null);
  readonly saveError = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly editing = signal<Producer | null>(null);
  form: ProducerForm = blankForm();

  ngOnInit(): void {
    void this.loadProducers();
  }

  async loadProducers(): Promise<void> {
    this.loading.set(true);
    this.listError.set(null);
    try {
      const result = await firstValueFrom(
        this.http.get<ProducerPage>('/api/v1/admin/producers', {
          params: { page: this.page(), size: 20 },
          withCredentials: true,
        }),
      );
      this.producers.set(result.content);
      this.totalPages.set(result.totalPages);
      this.totalElements.set(result.totalElements);
    } catch {
      this.listError.set('Não foi possível carregar os produtores. Tente novamente.');
    } finally {
      this.loading.set(false);
    }
  }

  changePage(page: number): void {
    if (page < 0 || (this.totalPages() > 0 && page >= this.totalPages())) return;
    this.page.set(page);
    void this.loadProducers();
  }

  startCreate(): void {
    this.editing.set(null);
    this.form = blankForm();
    this.saveError.set(null);
    this.notice.set(null);
  }

  resetAfterCreate(): void {
    this.editing.set(null);
    this.form = blankForm();
    this.saveError.set(null);
  }

  startEdit(producer: Producer): void {
    this.editing.set(producer);
    this.form = {
      slug: producer.slug,
      displayName: producer.displayName,
      originLabel: producer.originLabel,
      description: producer.description,
      active: producer.active,
    };
    this.saveError.set(null);
    this.notice.set(null);
  }

  async save(): Promise<void> {
    this.saving.set(true);
    this.saveError.set(null);
    this.notice.set(null);
    const editing = this.editing();
    const payload: ProducerWrite = {
      slug: this.form.slug.trim(),
      displayName: this.form.displayName.trim(),
      originLabel: this.form.originLabel.trim(),
      description: this.form.description.trim(),
    };
    try {
      if (editing) {
        const update: ProducerUpdate = { ...payload, active: this.form.active };
        await firstValueFrom(
          this.http.patch<Producer>(`/api/v1/admin/producers/${editing.id}`, update, {
            withCredentials: true,
          }),
        );
        this.notice.set(
          this.form.active
            ? 'Produtor atualizado.'
            : 'Produtor desativado. O cadastro e suas referências foram preservados.',
        );
      } else {
        await firstValueFrom(
          this.http.post<Producer>('/api/v1/admin/producers', payload, { withCredentials: true }),
        );
        this.notice.set('Produtor criado. Conteúdo marcado como demonstração.');
      }
      await this.loadProducers();
      if (this.listError())
        this.saveError.set('Alteração salva, mas a lista não foi atualizada. Tente recarregar.');
      else if (!editing) this.resetAfterCreate();
    } catch {
      this.saveError.set('Não foi possível salvar. Confira os dados ou tente novamente.');
    } finally {
      this.saving.set(false);
    }
  }
}
