import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

interface ProducerPage {
  slug: string;
  displayName: string;
  originLabel: string;
  description: string;
  products: {
    slug: string;
    displayName: string;
    category: string;
    skus: { priceCents: number; availableUnits: number }[];
  }[];
  page: number;
  totalPages: number;
}

@Component({
  imports: [RouterLink],
  selector: 'app-producer-detail',
  styleUrl: './producer-detail.component.css',
  templateUrl: './producer-detail.component.html',
})
export class ProducerDetailComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  readonly producer = signal<ProducerPage | null>(null);
  readonly loading = signal(true);
  readonly error = signal(false);

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => void this.load(params.get('slug') ?? ''));
  }

  async load(slug: string): Promise<void> {
    this.loading.set(true);
    this.error.set(false);
    try {
      this.producer.set(
        await firstValueFrom(this.http.get<ProducerPage>(`/api/v1/producers/${slug}`)),
      );
    } catch {
      this.producer.set(null);
      this.error.set(true);
    } finally {
      this.loading.set(false);
    }
  }
}
