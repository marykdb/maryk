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
                        { label: 'Data Design', slug: 'data-modeling/data-design' },
                        { label: 'Data Models', slug: 'data-modeling/datamodels' },
                        { label: 'Keys', slug: 'data-modeling/keys' },
                        { label: 'Versioning', slug: 'data-modeling/versioning' },
                        { label: 'Properties', slug: 'data-modeling/properties' },
                        { label: 'Property References', slug: 'data-modeling/properties/references' },
                        {
                            label: 'Property types',
                            collapsed: true,
                            items: [
                                // Types (common primitives first)
                                { label: 'String', slug: 'data-modeling/properties/types/string' },
                                { label: 'Number', slug: 'data-modeling/properties/types/number' },
                                { label: 'Boolean', slug: 'data-modeling/properties/types/boolean' },
                                { label: 'Enum', slug: 'data-modeling/properties/types/enum' },
                                { label: 'Date', slug: 'data-modeling/properties/types/date' },
                                { label: 'Time', slug: 'data-modeling/properties/types/time' },
                                { label: 'DateTime', slug: 'data-modeling/properties/types/datetime' },
                                { label: 'GeoPoint', slug: 'data-modeling/properties/types/geopoint' },
                                { label: 'Reference', slug: 'data-modeling/properties/types/reference' },
                                { label: 'FixedBytes', slug: 'data-modeling/properties/types/fixedbytes' },
                                { label: 'FlexBytes', slug: 'data-modeling/properties/types/flexbytes' },
                                { label: 'List', slug: 'data-modeling/properties/types/list' },
                                { label: 'Set', slug: 'data-modeling/properties/types/set' },
                                { label: 'Map', slug: 'data-modeling/properties/types/map' },
                                { label: 'Incrementing Map', slug: 'data-modeling/properties/types/incrementing-map' },
                                { label: 'Embedded Object', slug: 'data-modeling/properties/types/embedded-object' },
                                { label: 'Embedded Values', slug: 'data-modeling/properties/types/embedded-values' },
                                { label: 'Multi Type', slug: 'data-modeling/properties/types/multi-type' },
                                { label: 'Value Object', slug: 'data-modeling/properties/types/value-object' },
                            ],
                        },
                    ]
                },
				{
					label: 'Querying',
					items: [
                        { label: 'Overview', slug: 'querying' },
                        { label: 'Selecting with Graphs', slug: 'querying/reference-graphs' },
                        { label: 'Filters', slug: 'querying/filters' },
                        { label: 'Aggregations', slug: 'querying/aggregations' },
                        { label: 'Collect & Inject', slug: 'querying/collect-inject' },
					],
				},
                {
                    label: 'Serialization',
                    items: [
                        { label: 'Overview', slug: 'serialization' },
                        { label: 'JSON', slug: 'serialization/json' },
                        { label: 'YAML', slug: 'serialization/yaml' },
                        { label: 'ProtoBuf', slug: 'serialization/protobuf' },
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
