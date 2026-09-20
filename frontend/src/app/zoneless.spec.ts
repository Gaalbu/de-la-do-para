import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

/**
 * Prova comportamento zoneless (C10): sem zone.js carregado e renderização
 * dirigida por signals sem depender de zonas.
 */
describe('zoneless change detection', () => {
  it('runs without Zone.js', () => {
    const zone = (globalThis as { Zone?: unknown }).Zone;
    expect(zone).toBeUndefined();
  });

  it('renders signal updates without zones', () => {
    @Component({
      selector: 'dlp-probe',
      template: '<p>{{ value() }}</p>',
    })
    class Probe {
      readonly value = signal('antes');
    }

    const fixture = TestBed.createComponent(Probe);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('antes');

    fixture.componentInstance.value.set('depois');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('depois');
  });
});
