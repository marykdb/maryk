// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

// https://astro.build/config
export default defineConfig({
  vite: {
    resolve: {
      alias: {
        // Alias to reuse markdown from the repo's core/docs
        '@coredocs': resolve(fileURLToPath(new URL('.', import.meta.url)), '../core/docs'),
      },
    },
  },
	integrations: [
		starlight({
			title: 'Maryk',
			logo: { src: './src/assets/maryk-logo-optimized.webp', alt: 'Maryk' },
			description: 'Kotlin Multiplatform data modeling, versioned storage, and querying.',
			customCss: ['./src/styles/override.css'],
			social: [
				{ icon: 'github', label: 'GitHub', href: 'https://github.com/marykdb/maryk' },
			],
			sidebar: [
				{
					label: 'Introduction',
					items: [
                        { label: 'What is Maryk?', slug: 'what' },
                        { label: 'Why choose Maryk?', slug: 'why' },
						{ label: 'Getting Started', slug: 'getting-started' },
					],
				},
                {
                    label: 'Data Modeling',
                    items: [
                        { label: 'Data Models', slug: 'core-concepts/datamodels' },
                        { label: 'Keys', slug: 'core-concepts/keys' },
                        { label: 'Versioning', slug: 'core-concepts/versioning' },
                        { label: 'Properties', slug: 'core-concepts/properties' },
                        { label: 'Property References', slug: 'core-concepts/properties/references' },
                        {
                            label: 'Property types',
                            collapsed: true,
                            items: [
                                // Types (common primitives first)
                                { label: 'String', slug: 'core-concepts/properties/types/string' },
                                { label: 'Number', slug: 'core-concepts/properties/types/number' },
                                { label: 'Boolean', slug: 'core-concepts/properties/types/boolean' },
                                { label: 'Enum', slug: 'core-concepts/properties/types/enum' },
                                { label: 'Date', slug: 'core-concepts/properties/types/date' },
                                { label: 'Time', slug: 'core-concepts/properties/types/time' },
                                { label: 'DateTime', slug: 'core-concepts/properties/types/datetime' },
                                { label: 'GeoPoint', slug: 'core-concepts/properties/types/geopoint' },
                                { label: 'Reference', slug: 'core-concepts/properties/types/reference' },
                                { label: 'FixedBytes', slug: 'core-concepts/properties/types/fixedbytes' },
                                { label: 'FlexBytes', slug: 'core-concepts/properties/types/flexbytes' },
                                { label: 'List', slug: 'core-concepts/properties/types/list' },
                                { label: 'Set', slug: 'core-concepts/properties/types/set' },
                                { label: 'Map', slug: 'core-concepts/properties/types/map' },
                                { label: 'Incrementing Map', slug: 'core-concepts/properties/types/incrementing-map' },
                                { label: 'Embedded Object', slug: 'core-concepts/properties/types/embedded-object' },
                                { label: 'Embedded Values', slug: 'core-concepts/properties/types/embedded-values' },
                                { label: 'Multi Type', slug: 'core-concepts/properties/types/multi-type' },
                                { label: 'Value Object', slug: 'core-concepts/properties/types/value-object' },
                            ],
                        },
                    ]
                },
				{
					label: 'Querying',
					items: [
                        { label: 'Overview', slug: 'core-concepts/querying' },
                        { label: 'Selecting with Graphs', slug: 'core-concepts/querying/reference-graphs' },
                        { label: 'Filters', slug: 'core-concepts/querying/filters' },
                        { label: 'Aggregations', slug: 'core-concepts/querying/aggregations' },
                        { label: 'Collect & Inject', slug: 'core-concepts/querying/collect-inject' },
					],
				},
                {
                    label: 'Serialization',
                    items: [
                        { label: 'Overview', slug: 'core-concepts/serialization' },
                        { label: 'ProtoBuf Transport', slug: 'core-concepts/serialization/protobuf-transport' },
                    ],
                },
				{
					label: 'Stores',
					items: [
						{ label: 'Overview', slug: 'stores' },
                        {
                            label: 'Memory Store',
                            collapsed: true,
                            items: [
                                { label: 'Overview', slug: 'stores/memory' },
                            ],
                        },
                        {
                            label: 'RocksDB',
                            collapsed: true,
                            items: [
                                { label: 'RocksDB Store', slug: 'stores/rocksdb' },
                                { label: 'RocksDB Storage Layout', slug: 'stores/rocksdb/storage' },
                            ],
                        },
                        {
                            label: 'FoundationDB',
                            collapsed: true,
                            items: [
                                { label: 'Overview', slug: 'stores/foundationdb' },
                                { label: 'Architecture', slug: 'stores/foundationdb/architecture' },
                                { label: 'Local Testing', slug: 'stores/foundationdb/local-testing' },
                                { label: 'Storage Layout', slug: 'stores/foundationdb/storage' },
                            ],
                        },
					],
				},
			],
		}),
	],
});
