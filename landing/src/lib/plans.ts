import { FRONTEND_URL } from './constants';
import { useTranslations, type Lang } from '../i18n/utils';

export interface Plan {
	id: 'FREE' | 'STARTER' | 'PRO' | 'ENTERPRISE';
	name: string;
	price: string;
	period: string;
	/** Annual-billing price + period. Absent for plans with no annual variant. */
	priceAnnual?: string;
	periodAnnual?: string;
	/** Small line shown under the price in annual mode (e.g. "2 meses gratis"). */
	annualNote?: string;
	tagline: string;
	features: string[];
	cta: string;
	href: string;
	highlighted: boolean;
}

export function getPlans(lang: Lang): Plan[] {
	const t = useTranslations(lang);
	return [
		{
			id: 'FREE',
			name: 'Free',
			price: '$0',
			period: t('plan.perMonth'),
			priceAnnual: '$0',
			periodAnnual: t('plan.perYear'),
			tagline: t('plan.free.tagline'),
			features: [t('plan.free.f1'), t('plan.free.f2'), t('plan.free.f3'), t('plan.free.f4')],
			cta: t('plan.free.cta'),
			href: `${FRONTEND_URL}/register`,
			highlighted: false,
		},
		{
			id: 'STARTER',
			name: 'Starter',
			price: '$19',
			period: t('plan.perMonth'),
			priceAnnual: '$190',
			periodAnnual: t('plan.perYear'),
			annualNote: t('ppage.billing.save'),
			tagline: t('plan.starter.tagline'),
			features: [
				t('plan.starter.f1'),
				t('plan.starter.f2'),
				t('plan.starter.f3'),
				t('plan.starter.f4'),
			],
			cta: t('plan.starter.cta'),
			href: `${FRONTEND_URL}/register`,
			highlighted: false,
		},
		{
			id: 'PRO',
			name: 'Pro',
			price: '$49',
			period: t('plan.perMonth'),
			priceAnnual: '$490',
			periodAnnual: t('plan.perYear'),
			annualNote: t('ppage.billing.save'),
			tagline: t('plan.pro.tagline'),
			features: [t('plan.pro.f1'), t('plan.pro.f2'), t('plan.pro.f3'), t('plan.pro.f4')],
			cta: t('plan.pro.cta'),
			href: `${FRONTEND_URL}/register`,
			highlighted: true,
		},
		{
			id: 'ENTERPRISE',
			name: 'Enterprise',
			price: t('plan.priceCustom'),
			period: '',
			tagline: t('plan.ent.tagline'),
			features: [t('plan.ent.f1'), t('plan.ent.f2'), t('plan.ent.f3'), t('plan.ent.f4')],
			cta: t('plan.ent.cta'),
			href: 'mailto:tofernandoband01@outlook.com',
			highlighted: false,
		},
	];
}

/** A cell is `true` (incluido), `false` (no incluido) o un texto (límite / nivel). */
export type Cell = boolean | string;

export interface ComparisonRow {
	label: string;
	/** [Free, Starter, Pro, Enterprise] */
	values: [Cell, Cell, Cell, Cell];
}

export interface ComparisonGroup {
	category: string;
	rows: ComparisonRow[];
}

export function getComparison(lang: Lang): ComparisonGroup[] {
	const t = useTranslations(lang);
	const U = t('ptable.v.unlimited');
	return [
		{
			category: t('ptable.g.floor'),
			rows: [
				{ label: t('ptable.r.tables'), values: ['1', '10', U, U] },
				{ label: t('ptable.r.cart'), values: [true, true, true, true] },
				{ label: t('ptable.r.kds'), values: [true, true, true, true] },
				{ label: t('ptable.r.floor'), values: [false, true, true, true] },
				{ label: t('ptable.r.split'), values: [false, true, true, true] },
				{ label: t('ptable.r.cashclose'), values: [false, true, true, true] },
				{ label: t('ptable.r.print'), values: [false, true, true, true] },
				{ label: t('ptable.r.rooms'), values: [false, false, true, true] },
			],
		},
		{
			category: t('ptable.g.analytics'),
			rows: [
				{ label: t('ptable.r.metrics'), values: [true, true, true, true] },
				{ label: t('ptable.r.periodfilters'), values: [false, true, true, true] },
				{ label: t('ptable.r.advanced'), values: [false, false, true, true] },
				{ label: t('ptable.r.export'), values: [false, false, true, true] },
			],
		},
		{
			category: t('ptable.g.team'),
			rows: [
				{ label: t('ptable.r.roles'), values: [false, true, true, true] },
				{ label: t('ptable.r.staff'), values: [false, true, true, true] },
				{ label: t('ptable.r.multiwaiter'), values: [false, false, true, true] },
				{ label: t('ptable.r.branding'), values: [false, true, true, true] },
			],
		},
		{
			category: t('ptable.g.scale'),
			rows: [
				{ label: t('ptable.r.multibranch'), values: [false, false, false, true] },
				{ label: t('ptable.r.integrations'), values: [false, false, false, true] },
				{ label: t('ptable.r.sla'), values: [false, false, false, true] },
				{ label: t('ptable.r.am'), values: [false, false, false, true] },
				{
					label: t('ptable.r.support'),
					values: [
						t('ptable.v.community'),
						t('ptable.v.email'),
						t('ptable.v.priority'),
						t('ptable.v.dedicated'),
					],
				},
			],
		},
	];
}
