package com.startupsimulator.generator;

import com.startupsimulator.generator.spec.ProductSpec;
import com.startupsimulator.model.Startup;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Synthesizes domain-specific React 18 + TypeScript + Vite source code
 * and styles based on a ProductSpec.
 */
@Component
public class DomainAppSynthesizer {

    public Map<String, String> synthesizeFiles(Startup startup, ProductSpec spec) {
        Map<String, String> files = new LinkedHashMap<>();

        // 1. Root configuration files
        files.put("package.json", generatePackageJson(startup));
        files.put("vite.config.ts", generateViteConfig());
        files.put("tsconfig.json", generateTsConfig());
        files.put("tsconfig.node.json", generateTsConfigNode());
        files.put("index.html", generateIndexHtml(startup, spec));
        files.put("README.md", generateReadme(startup, spec));

        // 2. Base styles and bootstrapping
        files.put("src/main.tsx", generateMainTsx());
        files.put("src/index.css", generateIndexCss(spec));

        // 3. Domain components & views
        String category = spec.category().toLowerCase();

        if (category.contains("skincare") || category.contains("dermatol")) {
            synthesizeSkincareApp(startup, spec, files);
        } else if (category.contains("food") || category.contains("restaurant") || category.contains("culinary")) {
            synthesizeFoodDeliveryApp(startup, spec, files);
        } else if ((category.contains("bike") || category.contains("bicycle") || category.contains("velo"))
                && (category.contains("workshop") || category.contains("repair") || category.contains("service") || category.contains("operations"))) {
            synthesizeBikeWorkshopApp(startup, spec, files);
        } else if (category.contains("project") || category.contains("kanban") || category.contains("agile")) {
            synthesizeProjectManagementApp(startup, spec, files);
        } else if (category.contains("energy") || category.contains("grid") || category.contains("battery")) {
            synthesizeCleanEnergyApp(startup, spec, files);
        } else {
            synthesizeCustomApp(startup, spec, files);
        }

        return files;
    }

    private String generatePackageJson(Startup startup) {
        String slug = startup.getName().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (slug.isBlank()) slug = "startup-app";
        return """
        {
          "name": "%s",
          "private": true,
          "version": "0.1.0",
          "type": "module",
          "scripts": {
            "dev": "vite",
            "build": "tsc -b && vite build",
            "preview": "vite preview"
          },
          "dependencies": {
            "react": "^18.3.1",
            "react-dom": "^18.3.1"
          },
          "devDependencies": {
            "@types/react": "^18.3.11",
            "@types/react-dom": "^18.3.1",
            "@vitejs/plugin-react": "^4.3.3",
            "typescript": "^5.6.3",
            "vite": "^5.4.10"
          }
        }
        """.formatted(slug);
    }

    private String generateViteConfig() {
        return """
        import { defineConfig } from 'vite';
        import react from '@vitejs/plugin-react';

        export default defineConfig({
          plugins: [react()],
          server: {
            port: 5174,
            host: '127.0.0.1',
            strictPort: false
          },
          preview: {
            port: 5174,
            host: '127.0.0.1',
            strictPort: false
          }
        });
        """;
    }

    private String generateTsConfig() {
        return """
        {
          "compilerOptions": {
            "target": "ES2020",
            "useDefineForClassFields": true,
            "lib": ["ES2020", "DOM", "DOM.Iterable"],
            "module": "ESNext",
            "skipLibCheck": true,
            "moduleResolution": "bundler",
            "allowImportingTsExtensions": false,
            "resolveJsonModule": true,
            "isolatedModules": true,
            "noEmit": true,
            "jsx": "react-jsx",
            "strict": true,
            "noUnusedLocals": false,
            "noUnusedParameters": false,
            "noFallthroughCasesInSwitch": true
          },
          "include": ["src"]
        }
        """;
    }

    private String generateTsConfigNode() {
        return """
        {
          "compilerOptions": {
            "composite": true,
            "skipLibCheck": true,
            "module": "ESNext",
            "moduleResolution": "bundler",
            "allowSyntheticDefaultImports": true
          },
          "include": ["vite.config.ts"]
        }
        """;
    }

    private String generateIndexHtml(Startup startup, ProductSpec spec) {
        return """
        <!doctype html>
        <html lang="en">
          <head>
            <meta charset="UTF-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0" />
            <title>%s | %s</title>
            <meta name="description" content="%s" />
          </head>
          <body class="bg-slate-950 text-slate-100 antialiased selection:bg-indigo-500 selection:text-white">
            <div id="root"></div>
            <script type="module" src="/src/main.tsx"></script>
          </body>
        </html>
        """.formatted(escapeHtml(spec.appName()), escapeHtml(spec.category()), escapeHtml(spec.tagline()));
    }

    private String generateMainTsx() {
        return """
        import React from 'react';
        import ReactDOM from 'react-dom/client';
        import App from './App';
        import './index.css';

        ReactDOM.createRoot(document.getElementById('root')!).render(
          <React.StrictMode>
            <App />
          </React.StrictMode>
        );
        """;
    }

    private String generateIndexCss(ProductSpec spec) {
        String glow = spec.designSystem() != null ? spec.designSystem().accentGlow() : "rgba(99, 102, 241, 0.25)";
        return """
        @tailwind base;
        @tailwind components;
        @tailwind utilities;

        :root {
          font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
          color-scheme: dark;
          --accent-glow: %s;
        }

        body {
          margin: 0;
          min-height: 100vh;
          background-color: #020617;
          color: #f8fafc;
        }

        .glass-panel {
          background: rgba(15, 23, 42, 0.75);
          backdrop-filter: blur(12px);
          border: 1px solid rgba(255, 255, 255, 0.08);
        }

        .glow-accent {
          box-shadow: 0 0 35px -5px var(--accent-glow);
        }

        .scroll-slim::-webkit-scrollbar {
          width: 6px;
          height: 6px;
        }
        .scroll-slim::-webkit-scrollbar-track {
          background: rgba(15, 23, 42, 0.6);
        }
        .scroll-slim::-webkit-scrollbar-thumb {
          background: rgba(255, 255, 255, 0.15);
          border-radius: 9999px;
        }
        """.formatted(glow);
    }

    // =========================================================================
    // 1. SKINCARE APPLICATION SYNTHESIS
    // =========================================================================
    private void synthesizeSkincareApp(Startup startup, ProductSpec spec, Map<String, String> files) {
        // Types
        files.put("src/types.ts", """
        export interface RoutineStep {
          id: string;
          timeOfDay: 'AM' | 'PM';
          order: number;
          stepName: string;
          productName: string;
          activeIngredient: string;
          completed: boolean;
        }

        export interface ScannedProduct {
          id: string;
          brand: string;
          name: string;
          category: string;
          compatibilityScore: number;
          comedogenicRating: number; // 0-5
          keyActives: string[];
          isSafeForSensitive: boolean;
        }

        export interface Ingredient {
          id: string;
          name: string;
          category: 'Hydrator' | 'Active' | 'Exfoliant' | 'Antioxidant' | 'Soothing';
          benefits: string;
          safetyRating: 'Excellent' | 'Good' | 'Caution';
          comedogenicScore: number;
        }

        export interface SkinProfile {
          skinType: 'Dry' | 'Oily' | 'Combination' | 'Sensitive' | 'Normal';
          concerns: string[];
          sensitivityLevel: number; // 1-10
          oilinessLevel: number;    // 1-10
        }
        """);

        // Mock data
        files.put("src/mockData.ts", """
        import { RoutineStep, ScannedProduct, Ingredient } from './types';

        export const initialRoutineSteps: RoutineStep[] = [
          { id: '1', timeOfDay: 'AM', order: 1, stepName: 'Gentle Cleanser', productName: 'Hydrating Amino Gel', activeIngredient: 'Ceramides + Glycerin', completed: true },
          { id: '2', timeOfDay: 'AM', order: 2, stepName: 'Antioxidant Serum', productName: 'Pure Glow Complex', activeIngredient: '15% L-Ascorbic Acid + Ferulic', completed: false },
          { id: '3', timeOfDay: 'AM', order: 3, stepName: 'Barrier Moisturizer', productName: 'Ultra Ceramide Cream', activeIngredient: 'Niacinamide 2% + Squalane', completed: false },
          { id: '4', timeOfDay: 'AM', order: 4, stepName: 'Sunscreen Protection', productName: 'Mineral Shield SPF 50+', activeIngredient: '18% Zinc Oxide', completed: false },
          { id: '5', timeOfDay: 'PM', order: 1, stepName: 'Double Cleanse', productName: 'Oat Cleansing Balm', activeIngredient: 'Colloidal Oat 3%', completed: false },
          { id: '6', timeOfDay: 'PM', order: 2, stepName: 'Cellular Renewal', productName: 'Encapsulated Retinal 0.1%', activeIngredient: 'Retinaldehyde + Peptides', completed: false },
          { id: '7', timeOfDay: 'PM', order: 3, stepName: 'Night Recovery', productName: 'Lipid Repair Sleeping Mask', activeIngredient: 'Centella Asiatica + Ectoin', completed: false },
        ];

        export const libraryIngredients: Ingredient[] = [
          { id: 'ing-1', name: 'Niacinamide (Vitamin B3)', category: 'Active', benefits: 'Reduces sebum production, minimizes pore appearance, and repairs lipid barrier.', safetyRating: 'Excellent', comedogenicScore: 0 },
          { id: 'ing-2', name: 'Hyaluronic Acid Multi-Molecular', category: 'Hydrator', benefits: 'Penetrates dermis layers to bind 1000x its weight in cellular moisture.', safetyRating: 'Excellent', comedogenicScore: 0 },
          { id: 'ing-3', name: 'Salicylic Acid (BHA 2%)', category: 'Exfoliant', benefits: 'Lipid-soluble exfoliant clearing sebum plugs inside congested pores.', safetyRating: 'Good', comedogenicScore: 0 },
          { id: 'ing-4', name: 'Retinaldehyde 0.1%', category: 'Active', benefits: 'Direct precursor to retinoic acid; accelerates collagen synthesis 11x faster than retinol.', safetyRating: 'Good', comedogenicScore: 1 },
          { id: 'ing-5', name: 'Zinc PCA 1%', category: 'Soothing', benefits: 'Controls surface bacterial colonization while soothing redness.', safetyRating: 'Excellent', comedogenicScore: 0 },
          { id: 'ing-6', name: 'Centella Asiatica (Cica)', category: 'Soothing', benefits: 'Contains madecassoside which rapidly calms rosacea and barrier damage.', safetyRating: 'Excellent', comedogenicScore: 0 },
          { id: 'ing-7', name: 'Coconut Oil Fractionated', category: 'Hydrator', benefits: 'Deep emollient but known high comedogenic potential for acne-prone skin.', safetyRating: 'Caution', comedogenicScore: 4 },
        ];
        """);

        // Components: RoutineBuilder
        files.put("src/components/RoutineBuilder.tsx", """
        import React, { useState } from 'react';
        import { RoutineStep } from '../types';

        interface RoutineBuilderProps {
          steps: RoutineStep[];
          onToggleStep: (id: string) => void;
          onAddStep: (step: Omit<RoutineStep, 'id'>) => void;
        }

        export default function RoutineBuilder({ steps, onToggleStep, onAddStep }: RoutineBuilderProps) {
          const [selectedTime, setSelectedTime] = useState<'AM' | 'PM'>('AM');
          const [newStepName, setNewStepName] = useState('');
          const [newProductName, setNewProductName] = useState('');
          const [newActive, setNewActive] = useState('');
          const [showForm, setShowForm] = useState(false);

          const filtered = steps.filter(s => s.timeOfDay === selectedTime);
          const completedCount = filtered.filter(s => s.completed).length;

          const handleCreate = (e: React.FormEvent) => {
            e.preventDefault();
            if (!newStepName.trim() || !newProductName.trim()) return;
            onAddStep({
              timeOfDay: selectedTime,
              order: filtered.length + 1,
              stepName: newStepName.trim(),
              productName: newProductName.trim(),
              activeIngredient: newActive.trim() || 'Custom Formulation',
              completed: false,
            });
            setNewStepName('');
            setNewProductName('');
            setNewActive('');
            setShowForm(false);
          };

          return (
            <div className="space-y-6">
              {/* Header with AM/PM toggle */}
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-800 pb-4">
                <div>
                  <h2 className="text-xl font-bold text-white flex items-center gap-2">
                    <span>✨ Daily Skincare Regimen</span>
                    <span className="text-xs px-2.5 py-0.5 rounded-full bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
                      {completedCount} of {filtered.length} Done
                    </span>
                  </h2>
                  <p className="text-xs text-slate-400 mt-1">
                    Check off steps as you complete your routine. Data is saved in local browser state.
                  </p>
                </div>

                <div className="flex items-center gap-2">
                  <div className="bg-slate-900 p-1 rounded-xl border border-slate-800 flex items-center">
                    <button
                      onClick={() => setSelectedTime('AM')}
                      className={`px-4 py-1.5 rounded-lg text-xs font-bold transition flex items-center gap-1.5 ${
                        selectedTime === 'AM' ? 'bg-amber-500/20 text-amber-300 border border-amber-500/40' : 'text-slate-400 hover:text-white'
                      }`}
                    >
                      <span>☀️ Morning (AM)</span>
                    </button>
                    <button
                      onClick={() => setSelectedTime('PM')}
                      className={`px-4 py-1.5 rounded-lg text-xs font-bold transition flex items-center gap-1.5 ${
                        selectedTime === 'PM' ? 'bg-indigo-500/20 text-indigo-300 border border-indigo-500/40' : 'text-slate-400 hover:text-white'
                      }`}
                    >
                      <span>🌙 Evening (PM)</span>
                    </button>
                  </div>
                  <button
                    onClick={() => setShowForm(!showForm)}
                    className="px-3.5 py-2 rounded-xl text-xs font-semibold bg-emerald-600 hover:bg-emerald-500 text-white transition flex items-center gap-1"
                  >
                    <span>{showForm ? '✕ Close' : '+ Add Step'}</span>
                  </button>
                </div>
              </div>

              {/* Add Step Form */}
              {showForm && (
                <form onSubmit={handleCreate} className="glass-panel p-5 rounded-2xl border border-emerald-500/30 space-y-4 animate-fade-in">
                  <h3 className="text-sm font-bold text-white">Add Step to {selectedTime} Routine</h3>
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                    <div>
                      <label className="text-[11px] font-semibold text-slate-400 block mb-1">Step Type</label>
                      <input
                        type="text"
                        placeholder="e.g. Antioxidant Serum"
                        value={newStepName}
                        onChange={e => setNewStepName(e.target.value)}
                        className="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-1.5 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-emerald-500"
                        required
                      />
                    </div>
                    <div>
                      <label className="text-[11px] font-semibold text-slate-400 block mb-1">Product Name</label>
                      <input
                        type="text"
                        placeholder="e.g. C-Glow 15%"
                        value={newProductName}
                        onChange={e => setNewProductName(e.target.value)}
                        className="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-1.5 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-emerald-500"
                        required
                      />
                    </div>
                    <div>
                      <label className="text-[11px] font-semibold text-slate-400 block mb-1">Key Actives</label>
                      <input
                        type="text"
                        placeholder="e.g. L-Ascorbic Acid"
                        value={newActive}
                        onChange={e => setNewActive(e.target.value)}
                        className="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-1.5 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-emerald-500"
                      />
                    </div>
                  </div>
                  <div className="flex justify-end gap-2">
                    <button
                      type="button"
                      onClick={() => setShowForm(false)}
                      className="px-3 py-1.5 rounded-lg text-xs font-semibold bg-slate-800 text-slate-300"
                    >
                      Cancel
                    </button>
                    <button
                      type="submit"
                      className="px-4 py-1.5 rounded-lg text-xs font-bold bg-emerald-600 hover:bg-emerald-500 text-white"
                    >
                      Save Step
                    </button>
                  </div>
                </form>
              )}

              {/* Steps List */}
              <div className="space-y-3">
                {filtered.map((step, idx) => (
                  <div
                    key={step.id}
                    onClick={() => onToggleStep(step.id)}
                    className={`p-4 rounded-xl border transition cursor-pointer flex items-center justify-between gap-4 ${
                      step.completed
                        ? 'bg-slate-900/40 border-slate-800/80 opacity-75'
                        : 'glass-panel border-slate-700 hover:border-emerald-500/50'
                    }`}
                  >
                    <div className="flex items-center gap-3">
                      <div className={`w-8 h-8 rounded-lg flex items-center justify-center font-bold text-xs transition ${
                        step.completed ? 'bg-emerald-500 text-slate-950 font-black' : 'bg-slate-800 text-slate-400 border border-slate-700'
                      }`}>
                        {step.completed ? '✓' : idx + 1}
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="text-xs font-bold uppercase tracking-wider text-emerald-400">{step.stepName}</span>
                          {step.completed && <span className="text-[10px] text-slate-500 font-mono">(Completed)</span>}
                        </div>
                        <h4 className={`text-sm font-semibold text-white ${step.completed ? 'line-through text-slate-400' : ''}`}>
                          {step.productName}
                        </h4>
                        <p className="text-[11px] text-slate-400 mt-0.5">Actives: <span className="text-slate-300">{step.activeIngredient}</span></p>
                      </div>
                    </div>
                    <button
                      type="button"
                      className={`px-3 py-1 rounded-lg text-xs font-semibold transition ${
                        step.completed ? 'bg-slate-800 text-slate-400' : 'bg-emerald-600 hover:bg-emerald-500 text-white'
                      }`}
                    >
                      {step.completed ? 'Undo' : 'Mark Done'}
                    </button>
                  </div>
                ))}
              </div>
            </div>
          );
        }
        """);

        // Components: ProductScanner
        files.put("src/components/ProductScanner.tsx", """
        import React, { useState } from 'react';
        import { ScannedProduct } from '../types';

        interface ProductScannerProps {
          onAddScannedToRoutine: (product: ScannedProduct) => void;
        }

        export default function ProductScanner({ onAddScannedToRoutine }: ProductScannerProps) {
          const [scannedResult, setScannedResult] = useState<ScannedProduct | null>(null);
          const [scanning, setScanning] = useState(false);
          const [barcodeInput, setBarcodeInput] = useState('8901234567890');
          const [addedNotice, setAddedNotice] = useState(false);

          const sampleDatabase: ScannedProduct[] = [
            {
              id: 'scan-1',
              brand: 'K-Dermaceutic',
              name: 'Centella Barrier Gel Cleanser',
              category: 'Cleanser',
              compatibilityScore: 96,
              comedogenicRating: 0,
              keyActives: ['Centella Asiatica Extract 45%', 'Madecassoside', 'Betaine Salicylate 0.5%'],
              isSafeForSensitive: true,
            },
            {
              id: 'scan-2',
              brand: 'Cellular Science',
              name: 'Triple Peptide Firming Elixir',
              category: 'Serum',
              compatibilityScore: 88,
              comedogenicRating: 1,
              keyActives: ['Copper Tripeptide-1', 'Matrixyl 3000', 'Hyaluronic Acid 2%'],
              isSafeForSensitive: true,
            },
            {
              id: 'scan-3',
              brand: 'SunBiotic Lab',
              name: 'Mineral Waterdrop Fluid SPF 50',
              category: 'Sunscreen',
              compatibilityScore: 92,
              comedogenicRating: 0,
              keyActives: ['Zinc Oxide 19%', 'Ectoin 1%', 'Niacinamide 2%'],
              isSafeForSensitive: true,
            }
          ];

          const handleSimulateScan = (presetIdx: number) => {
            setScanning(true);
            setAddedNotice(false);
            setTimeout(() => {
              setScannedResult(sampleDatabase[presetIdx % sampleDatabase.length]);
              setScanning(false);
            }, 800);
          };

          const handleAdd = () => {
            if (!scannedResult) return;
            onAddScannedToRoutine(scannedResult);
            setAddedNotice(true);
            setTimeout(() => setAddedNotice(false), 3000);
          };

          return (
            <div className="space-y-6">
              <div className="border-b border-slate-800 pb-4">
                <h2 className="text-xl font-bold text-white flex items-center gap-2">
                  <span>📷 AI Ingredient & Barcode Scanner</span>
                  <span className="text-xs px-2.5 py-0.5 rounded-full bg-cyan-500/20 text-cyan-400 border border-cyan-500/30">
                    SIMULATED SCAN ENGINE
                  </span>
                </h2>
                <p className="text-xs text-slate-400 mt-1">
                  Point camera at any barcode or cosmetic ingredient list for instant dermatological breakdown.
                </p>
              </div>

              {/* Scanner Viewfinder / Dropzone */}
              <div className="glass-panel p-6 rounded-2xl border border-slate-800 space-y-4">
                <div
                  onClick={() => handleSimulateScan(0)}
                  className="h-44 border-2 border-dashed border-emerald-500/40 hover:border-emerald-400 rounded-xl flex flex-col items-center justify-center p-4 text-center cursor-pointer transition bg-slate-900/40 group"
                >
                  <div className="w-12 h-12 rounded-full bg-emerald-500/10 flex items-center justify-center text-2xl mb-2 group-hover:scale-110 transition-transform">
                    {scanning ? '⏳' : '✨'}
                  </div>
                  <p className="text-sm font-semibold text-white">
                    {scanning ? 'Analyzing chemical formulation…' : 'Click to Simulate Camera Scan on Sample Barcode'}
                  </p>
                  <p className="text-xs text-slate-400 mt-1">Supports EAN-13, UPC barcodes, and INCI ingredient labels</p>
                </div>

                {/* Quick Presets */}
                <div className="flex flex-wrap items-center justify-between gap-2 pt-2 text-xs">
                  <span className="text-slate-400 font-semibold">Test Products:</span>
                  <div className="flex gap-2">
                    <button
                      onClick={() => handleSimulateScan(0)}
                      className="px-3 py-1 rounded-lg bg-slate-900 hover:bg-slate-800 border border-slate-700 text-slate-300 transition"
                    >
                      Sample 1: Barrier Cleanser
                    </button>
                    <button
                      onClick={() => handleSimulateScan(1)}
                      className="px-3 py-1 rounded-lg bg-slate-900 hover:bg-slate-800 border border-slate-700 text-slate-300 transition"
                    >
                      Sample 2: Peptide Serum
                    </button>
                    <button
                      onClick={() => handleSimulateScan(2)}
                      className="px-3 py-1 rounded-lg bg-slate-900 hover:bg-slate-800 border border-slate-700 text-slate-300 transition"
                    >
                      Sample 3: Mineral SPF 50
                    </button>
                  </div>
                </div>
              </div>

              {/* Scanned Result Card */}
              {scannedResult && (
                <div className="glass-panel p-6 rounded-2xl border border-emerald-500/40 glow-accent space-y-4 animate-fade-in">
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-800 pb-3">
                    <div>
                      <span className="text-[10px] uppercase font-bold tracking-wider text-emerald-400">{scannedResult.brand} · {scannedResult.category}</span>
                      <h3 className="text-lg font-bold text-white">{scannedResult.name}</h3>
                    </div>
                    <div className="flex items-center gap-2">
                      <div className="text-right">
                        <div className="text-xs font-bold text-emerald-400">{scannedResult.compatibilityScore}% Match</div>
                        <div className="text-[10px] text-slate-400">Skin Compatibility</div>
                      </div>
                      <div className="h-10 w-10 rounded-full bg-emerald-500/20 border border-emerald-500/40 flex items-center justify-center font-mono font-bold text-emerald-300 text-sm">
                        {scannedResult.compatibilityScore}
                      </div>
                    </div>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
                    <div className="bg-slate-900/60 p-3 rounded-xl border border-slate-800">
                      <span className="text-slate-400 font-semibold block mb-1">Comedogenic Score (Pore Clogging):</span>
                      <span className="font-bold text-emerald-300">{scannedResult.comedogenicRating} / 5 (Non-comedogenic)</span>
                    </div>
                    <div className="bg-slate-900/60 p-3 rounded-xl border border-slate-800">
                      <span className="text-slate-400 font-semibold block mb-1">Sensitive Skin Safety:</span>
                      <span className="font-bold text-emerald-300">✓ 100% Free of synthetic fragrances & drying alcohol</span>
                    </div>
                  </div>

                  <div className="space-y-1.5">
                    <span className="text-xs font-semibold text-slate-400 block">Verified Active Compounds:</span>
                    <div className="flex flex-wrap gap-1.5">
                      {scannedResult.keyActives.map((act, i) => (
                        <span key={i} className="px-2.5 py-1 rounded-lg text-xs bg-slate-900 border border-slate-700 text-slate-200">
                          {act}
                        </span>
                      ))}
                    </div>
                  </div>

                  <div className="pt-2 flex items-center justify-between">
                    <button
                      onClick={handleAdd}
                      className="px-4 py-2 rounded-xl text-xs font-bold bg-emerald-600 hover:bg-emerald-500 text-white transition flex items-center gap-1.5 shadow-md shadow-emerald-600/30"
                    >
                      <span>+ Add to Routine Checklist</span>
                    </button>
                    {addedNotice && (
                      <span className="text-xs text-emerald-400 font-semibold animate-pulse">
                        ✓ Added to routine successfully!
                      </span>
                    )}
                  </div>
                </div>
              )}
            </div>
          );
        }
        """);

        // Components: IngredientGlossary
        files.put("src/components/IngredientGlossary.tsx", """
        import React, { useState } from 'react';
        import { libraryIngredients } from '../mockData';

        export default function IngredientGlossary() {
          const [search, setSearch] = useState('');
          const [activeCategory, setActiveCategory] = useState<string>('ALL');

          const categories = ['ALL', 'Active', 'Hydrator', 'Exfoliant', 'Soothing', 'Antioxidant'];

          const filtered = libraryIngredients.filter(ing => {
            const matchesSearch = ing.name.toLowerCase().includes(search.toLowerCase()) || ing.benefits.toLowerCase().includes(search.toLowerCase());
            const matchesCategory = activeCategory === 'ALL' || ing.category === activeCategory;
            return matchesSearch && matchesCategory;
          });

          return (
            <div className="space-y-6">
              <div className="border-b border-slate-800 pb-4">
                <h2 className="text-xl font-bold text-white flex items-center gap-2">
                  <span>🔬 Clinical Ingredient Analysis & Glossary</span>
                  <span className="text-xs px-2.5 py-0.5 rounded-full bg-indigo-500/20 text-indigo-400 border border-indigo-500/30">
                    {filtered.length} Ingredients
                  </span>
                </h2>
                <p className="text-xs text-slate-400 mt-1">
                  Search molecular safety ratings, toxicology profiles, and pore-clogging metrics.
                </p>
              </div>

              {/* Search & Filter Bar */}
              <div className="flex flex-col sm:flex-row gap-3">
                <input
                  type="text"
                  placeholder="Search ingredient (e.g. Niacinamide, Retinal, Hyaluronic, Cica)..."
                  value={search}
                  onChange={e => setSearch(e.target.value)}
                  className="flex-1 bg-slate-900 border border-slate-800 rounded-xl px-4 py-2.5 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-emerald-500"
                />
                <div className="flex flex-wrap gap-1">
                  {categories.map(c => (
                    <button
                      key={c}
                      onClick={() => setActiveCategory(c)}
                      className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
                        activeCategory === c ? 'bg-emerald-600 text-white' : 'bg-slate-900 text-slate-400 hover:text-white border border-slate-800'
                      }`}
                    >
                      {c}
                    </button>
                  ))}
                </div>
              </div>

              {/* Grid of Ingredients */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {filtered.map(ing => (
                  <div key={ing.id} className="glass-panel p-5 rounded-2xl border border-slate-800 space-y-3 hover:border-slate-700 transition">
                    <div className="flex items-start justify-between gap-2">
                      <div>
                        <span className="text-[10px] font-bold uppercase tracking-wider text-emerald-400">{ing.category}</span>
                        <h4 className="text-sm font-bold text-white">{ing.name}</h4>
                      </div>
                      <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                        ing.safetyRating === 'Excellent' ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30' :
                        ing.safetyRating === 'Good' ? 'bg-sky-500/20 text-sky-300 border border-sky-500/30' :
                        'bg-rose-500/20 text-rose-300 border border-rose-500/30'
                      }`}>
                        {ing.safetyRating} Safety
                      </span>
                    </div>

                    <p className="text-xs text-slate-300 leading-relaxed">{ing.benefits}</p>

                    <div className="pt-2 border-t border-slate-800/80 flex items-center justify-between text-[11px] text-slate-400">
                      <span>Comedogenic Rating: <b className="text-slate-200">{ing.comedogenicScore} / 5</b></span>
                      <span className="text-emerald-400">Dermatologically Verified</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          );
        }
        """);

        // Components: SkinProfile
        files.put("src/components/SkinProfile.tsx", """
        import React, { useState } from 'react';
        import { SkinProfile } from '../types';

        export default function SkinProfileView() {
          const [profile, setProfile] = useState<SkinProfile>({
            skinType: 'Combination',
            concerns: ['Pore Congestion', 'Sensitivity', 'Dehydration'],
            sensitivityLevel: 6,
            oilinessLevel: 5,
          });

          const skinTypes: SkinProfile['skinType'][] = ['Oily', 'Dry', 'Combination', 'Sensitive', 'Normal'];
          const availableConcerns = ['Acne & Blemishes', 'Pore Congestion', 'Redness / Rosacea', 'Fine Lines', 'Hyperpigmentation', 'Dehydration'];

          const toggleConcern = (concern: string) => {
            if (profile.concerns.includes(concern)) {
              setProfile({ ...profile, concerns: profile.concerns.filter(c => c !== concern) });
            } else {
              setProfile({ ...profile, concerns: [...profile.concerns, concern] });
            }
          };

          return (
            <div className="space-y-6">
              <div className="border-b border-slate-800 pb-4">
                <h2 className="text-xl font-bold text-white flex items-center gap-2">
                  <span>🧬 Clinical Skin Assessment & Diagnostic Profile</span>
                </h2>
                <p className="text-xs text-slate-400 mt-1">
                  Fine-tune your physiological skin traits to calibrate customized daily regimen suggestions.
                </p>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {/* Form controls */}
                <div className="glass-panel p-6 rounded-2xl border border-slate-800 space-y-5">
                  <div>
                    <label className="text-xs font-bold text-slate-300 block mb-2">Primary Skin Type</label>
                    <div className="grid grid-cols-3 gap-2">
                      {skinTypes.map(t => (
                        <button
                          key={t}
                          onClick={() => setProfile({ ...profile, skinType: t })}
                          className={`p-2.5 rounded-xl text-xs font-semibold transition ${
                            profile.skinType === t ? 'bg-emerald-600 text-white' : 'bg-slate-900 border border-slate-800 text-slate-400 hover:text-white'
                          }`}
                        >
                          {t}
                        </button>
                      ))}
                    </div>
                  </div>

                  <div>
                    <label className="text-xs font-bold text-slate-300 block mb-2">Targeted Concerns</label>
                    <div className="grid grid-cols-2 gap-2">
                      {availableConcerns.map(c => {
                        const active = profile.concerns.includes(c);
                        return (
                          <button
                            key={c}
                            onClick={() => toggleConcern(c)}
                            className={`p-2 rounded-lg text-[11px] font-semibold text-left transition ${
                              active ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/40' : 'bg-slate-900 border border-slate-800 text-slate-400 hover:text-white'
                            }`}
                          >
                            {active ? '✓ ' : '+ '} {c}
                          </button>
                        );
                      })}
                    </div>
                  </div>

                  <div className="space-y-3">
                    <div className="flex justify-between text-xs">
                      <span className="font-semibold text-slate-300">Sensitivity Level:</span>
                      <span className="font-mono text-emerald-400">{profile.sensitivityLevel} / 10</span>
                    </div>
                    <input
                      type="range"
                      min="1"
                      max="10"
                      value={profile.sensitivityLevel}
                      onChange={e => setProfile({ ...profile, sensitivityLevel: Number(e.target.value) })}
                      className="w-full accent-emerald-500 cursor-pointer"
                    />
                  </div>
                </div>

                {/* Diagnostic Prescription Output */}
                <div className="glass-panel p-6 rounded-2xl border border-emerald-500/30 space-y-4">
                  <span className="text-[10px] uppercase font-bold tracking-wider text-emerald-400">Diagnostic Summary</span>
                  <h3 className="text-lg font-bold text-white">Recommended Formulation Protocol</h3>
                  <div className="space-y-3 text-xs">
                    <div className="p-3 bg-slate-900/60 rounded-xl border border-slate-800">
                      <span className="font-semibold text-slate-200 block mb-1">Recommended Actives:</span>
                      <p className="text-slate-400 leading-relaxed">
                        Azelaic Acid 10%, Niacinamide 2%, and Centella Asiatica. Avoid harsh mechanical scrubs or high-strength AHA peelings while sensitivity is at {profile.sensitivityLevel}/10.
                      </p>
                    </div>
                    <div className="p-3 bg-slate-900/60 rounded-xl border border-slate-800">
                      <span className="font-semibold text-slate-200 block mb-1">Sunscreen Recommendation:</span>
                      <p className="text-slate-400 leading-relaxed">
                        Pure physical mineral filter (Zinc Oxide) without fragrance to prevent inflammatory flushing.
                      </p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          );
        }
        """);

        // Main App.tsx for Skincare
        files.put("src/App.tsx", """
        import React, { useState, useEffect } from 'react';
        import { RoutineStep, ScannedProduct } from './types';
        import { initialRoutineSteps } from './mockData';
        import RoutineBuilder from './components/RoutineBuilder';
        import ProductScanner from './components/ProductScanner';
        import IngredientGlossary from './components/IngredientGlossary';
        import SkinProfileView from './components/SkinProfile';

        export default function App() {
          const [activeTab, setActiveTab] = useState<'routine' | 'scanner' | 'glossary' | 'profile'>('routine');
          const [steps, setSteps] = useState<RoutineStep[]>(() => {
            const saved = localStorage.getItem('skincare_routine_steps');
            return saved ? JSON.parse(saved) : initialRoutineSteps;
          });

          useEffect(() => {
            localStorage.setItem('skincare_routine_steps', JSON.stringify(steps));
          }, [steps]);

          const handleToggleStep = (id: string) => {
            setSteps(prev => prev.map(s => s.id === id ? { ...s, completed: !s.completed } : s));
          };

          const handleAddStep = (newStep: Omit<RoutineStep, 'id'>) => {
            setSteps(prev => [...prev, { ...newStep, id: 'step-' + Date.now() }]);
          };

          const handleAddScannedToRoutine = (product: ScannedProduct) => {
            setSteps(prev => [
              ...prev,
              {
                id: 'scanned-' + Date.now(),
                timeOfDay: 'AM',
                order: prev.length + 1,
                stepName: product.category,
                productName: product.name,
                activeIngredient: product.keyActives.join(', '),
                completed: false,
              }
            ]);
            setActiveTab('routine');
          };

          return (
            <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans">
              {/* Header */}
              <header className="border-b border-slate-800/80 bg-slate-900/60 backdrop-blur-md px-6 py-4 sticky top-0 z-30 flex flex-wrap items-center justify-between gap-4">
                <div className="flex items-center gap-3">
                  <div className="h-9 w-9 rounded-xl bg-gradient-to-tr from-emerald-500 to-teal-400 flex items-center justify-center font-bold text-slate-950 shadow-md">
                    ✨
                  </div>
                  <div>
                    <h1 className="font-bold text-white text-base tracking-wide">%s</h1>
                    <span className="text-[10px] text-emerald-400 font-mono">Personalized Dermatology Companion</span>
                  </div>
                </div>

                <nav className="flex items-center gap-1 text-xs font-medium bg-slate-950 p-1 rounded-xl border border-slate-800">
                  <button
                    onClick={() => setActiveTab('routine')}
                    className={`px-3 py-1.5 rounded-lg transition ${activeTab === 'routine' ? 'bg-emerald-600 text-white font-semibold' : 'text-slate-400 hover:text-white'}`}
                  >
                    ✨ My Routine
                  </button>
                  <button
                    onClick={() => setActiveTab('scanner')}
                    className={`px-3 py-1.5 rounded-lg transition ${activeTab === 'scanner' ? 'bg-emerald-600 text-white font-semibold' : 'text-slate-400 hover:text-white'}`}
                  >
                    📷 Product Scanner
                  </button>
                  <button
                    onClick={() => setActiveTab('glossary')}
                    className={`px-3 py-1.5 rounded-lg transition ${activeTab === 'glossary' ? 'bg-emerald-600 text-white font-semibold' : 'text-slate-400 hover:text-white'}`}
                  >
                    🔬 Ingredient Analysis
                  </button>
                  <button
                    onClick={() => setActiveTab('profile')}
                    className={`px-3 py-1.5 rounded-lg transition ${activeTab === 'profile' ? 'bg-emerald-600 text-white font-semibold' : 'text-slate-400 hover:text-white'}`}
                  >
                    🧬 Skin Profile
                  </button>
                </nav>
              </header>

              {/* Simulation Notice Banner */}
              <div className="bg-emerald-950/40 border-b border-emerald-500/20 px-6 py-2 text-[11px] text-emerald-300 flex items-center justify-between">
                <span>🛡️ %s</span>
                <span className="font-mono text-emerald-400/80">Local Storage Active</span>
              </div>

              {/* Main Content */}
              <main className="flex-1 max-w-6xl w-full mx-auto px-4 py-8">
                {activeTab === 'routine' && (
                  <RoutineBuilder steps={steps} onToggleStep={handleToggleStep} onAddStep={handleAddStep} />
                )}
                {activeTab === 'scanner' && (
                  <ProductScanner onAddScannedToRoutine={handleAddScannedToRoutine} />
                )}
                {activeTab === 'glossary' && (
                  <IngredientGlossary />
                )}
                {activeTab === 'profile' && (
                  <SkinProfileView />
                )}
              </main>

              {/* Footer */}
              <footer className="border-t border-slate-800/60 bg-slate-900/40 py-6 px-4 mt-auto">
                <div className="max-w-6xl mx-auto flex flex-col md:flex-row items-center justify-between gap-4 text-xs text-slate-500">
                  <p>© 2026 %s · Real standalone application generated in isolated workspace.</p>
                  <p className="flex items-center gap-1.5">
                    <span className="h-2 w-2 rounded-full bg-emerald-400 inline-block" />
                    <span>Active Domain Engine: Skincare & Dermatology</span>
                  </p>
                </div>
              </footer>
            </div>
          );
        }
        """.formatted(escapeJs(startup.getName()), escapeJs(spec.simulatedNotice()), escapeJs(startup.getName())));
    }

    // =========================================================================
    // 2. FOOD DELIVERY APPLICATION SYNTHESIS
    // =========================================================================
    private void synthesizeFoodDeliveryApp(Startup startup, ProductSpec spec, Map<String, String> files) {
        // Types
        files.put("src/types.ts", """
        export interface Restaurant {
          id: string;
          name: string;
          cuisine: string;
          rating: number;
          deliveryMinutes: number;
          minOrder: number;
          deliveryFee: number;
          imageEmoji: string;
        }

        export interface MenuItem {
          id: string;
          restaurantId: string;
          name: string;
          description: string;
          price: number;
          category: string;
          dietary: 'Veg' | 'Non-Veg' | 'Gluten-Free' | 'Chef Special';
        }

        export interface CartItem {
          menuItem: MenuItem;
          quantity: number;
        }

        export interface OrderState {
          id: string;
          items: CartItem[];
          subtotal: number;
          deliveryFee: number;
          tax: number;
          total: number;
          status: 'RECEIVED' | 'PREPARING' | 'ON_THE_WAY' | 'DELIVERED';
          estimatedArrivalMinutes: number;
        }
        """);

        // Mock data
        files.put("src/mockData.ts", """
        import { Restaurant, MenuItem } from './types';

        export const initialRestaurants: Restaurant[] = [
          { id: 'rest-1', name: 'Nori & Sesame Artisanal Sushi', cuisine: 'Japanese / Sushi', rating: 4.9, deliveryMinutes: 25, minOrder: 20, deliveryFee: 2.99, imageEmoji: '🍣' },
          { id: 'rest-2', name: 'Firenze Stone-Baked Pizza', cuisine: 'Italian / Pizza', rating: 4.8, deliveryMinutes: 30, minOrder: 15, deliveryFee: 1.99, imageEmoji: '🍕' },
          { id: 'rest-3', name: 'Smash & Brioche Gourmet Burgers', cuisine: 'American / Burgers', rating: 4.7, deliveryMinutes: 20, minOrder: 12, deliveryFee: 2.49, imageEmoji: '🍔' },
          { id: 'rest-4', name: 'Green Botanica Organic Bowls', cuisine: 'Healthy / Vegan', rating: 4.9, deliveryMinutes: 20, minOrder: 15, deliveryFee: 0.00, imageEmoji: '🥗' },
        ];

        export const initialMenuItems: MenuItem[] = [
          { id: 'm-1', restaurantId: 'rest-1', name: 'Truffle Salmon Nigiri Set (8 pcs)', description: 'Flame-seared Atlantic salmon with black truffle paste and micro shiso.', price: 18.50, category: 'Sushi', dietary: 'Chef Special' },
          { id: 'm-2', restaurantId: 'rest-1', name: 'Dragon Crunch Tempura Roll', description: 'Crispy prawn tempura, avocado, tobiko, and unagi glaze.', price: 16.00, category: 'Sushi', dietary: 'Chef Special' },
          { id: 'm-3', restaurantId: 'rest-1', name: 'Spicy Edamame with Sea Salt', description: 'Wok-charred soybeans with togarashi chili and Maldon flakes.', price: 6.50, category: 'Starters', dietary: 'Veg' },
          { id: 'm-4', restaurantId: 'rest-2', name: 'Bufala Margherita D.O.P.', description: 'San Marzano tomato sauce, fresh buffalo mozzarella, fresh basil.', price: 15.00, category: 'Pizza', dietary: 'Veg' },
          { id: 'm-5', restaurantId: 'rest-2', name: 'Diavola Calabrian Salami', description: 'Spicy soppressata, chili oil, fior di latte, and honey drizzle.', price: 17.50, category: 'Pizza', dietary: 'Non-Veg' },
          { id: 'm-6', restaurantId: 'rest-3', name: 'Double Wagyu Bacon Smash', description: 'Dry-aged wagyu patties, smoked gouda, bacon onion jam, brioche bun.', price: 14.50, category: 'Burgers', dietary: 'Non-Veg' },
          { id: 'm-7', restaurantId: 'rest-4', name: 'Superfood Quinoa & Tahini Bowl', description: 'Kale, roasted sweet potato, edamame, avocado, golden tahini dressing.', price: 13.00, category: 'Bowls', dietary: 'Veg' },
        ];
        """);

        // Components: RestaurantCatalog
        files.put("src/components/RestaurantCatalog.tsx", """
        import React, { useState } from 'react';
        import { Restaurant } from '../types';

        interface RestaurantCatalogProps {
          restaurants: Restaurant[];
          onSelectRestaurant: (r: Restaurant) => void;
        }

        export default function RestaurantCatalog({ restaurants, onSelectRestaurant }: RestaurantCatalogProps) {
          const [search, setSearch] = useState('');
          const [cuisineFilter, setCuisineFilter] = useState('ALL');

          const cuisines = ['ALL', 'Sushi', 'Pizza', 'Burgers', 'Healthy'];

          const filtered = restaurants.filter(r => {
            const matchesSearch = r.name.toLowerCase().includes(search.toLowerCase()) || r.cuisine.toLowerCase().includes(search.toLowerCase());
            const matchesCuisine = cuisineFilter === 'ALL' || r.cuisine.toLowerCase().includes(cuisineFilter.toLowerCase());
            return matchesSearch && matchesCuisine;
          });

          return (
            <div className="space-y-6">
              <div className="border-b border-slate-800 pb-4">
                <h2 className="text-xl font-bold text-white flex items-center gap-2">
                  <span>🍔 Featured Kitchens & Restaurants</span>
                  <span className="text-xs px-2.5 py-0.5 rounded-full bg-amber-500/20 text-amber-300 border border-amber-500/30">
                    {filtered.length} Open Now
                  </span>
                </h2>
                <p className="text-xs text-slate-400 mt-1">
                  Discover top-rated local dining partners with real-time delivery estimates.
                </p>
              </div>

              {/* Search & Filters */}
              <div className="flex flex-col sm:flex-row gap-3">
                <input
                  type="text"
                  placeholder="Search restaurants, dishes, or cuisines..."
                  value={search}
                  onChange={e => setSearch(e.target.value)}
                  className="flex-1 bg-slate-900 border border-slate-800 rounded-xl px-4 py-2.5 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-amber-500"
                />
                <div className="flex flex-wrap gap-1">
                  {cuisines.map(c => (
                    <button
                      key={c}
                      onClick={() => setCuisineFilter(c)}
                      className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
                        cuisineFilter === c ? 'bg-amber-600 text-white' : 'bg-slate-900 text-slate-400 hover:text-white border border-slate-800'
                      }`}
                    >
                      {c}
                    </button>
                  ))}
                </div>
              </div>

              {/* Restaurants Grid */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {filtered.map(r => (
                  <div
                    key={r.id}
                    onClick={() => onSelectRestaurant(r)}
                    className="glass-panel p-5 rounded-2xl border border-slate-800 hover:border-amber-500/50 transition cursor-pointer group flex items-start gap-4"
                  >
                    <div className="w-16 h-16 rounded-2xl bg-amber-500/10 border border-amber-500/20 flex items-center justify-center text-3xl group-hover:scale-105 transition-transform">
                      {r.imageEmoji}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center justify-between gap-2">
                        <h3 className="font-bold text-white text-base truncate group-hover:text-amber-300 transition-colors">
                          {r.name}
                        </h3>
                        <span className="flex items-center gap-1 text-xs font-bold text-amber-400 bg-amber-500/10 px-2 py-0.5 rounded-md">
                          ★ {r.rating}
                        </span>
                      </div>
                      <p className="text-xs text-slate-400 mt-1">{r.cuisine}</p>

                      <div className="flex items-center gap-3 mt-3 text-[11px] text-slate-400">
                        <span className="flex items-center gap-1">
                          ⏱ {r.deliveryMinutes} mins
                        </span>
                        <span>•</span>
                        <span>
                          {r.deliveryFee === 0 ? <b className="text-emerald-400">FREE Delivery</b> : `$${r.deliveryFee.toFixed(2)} delivery`}
                        </span>
                        <span>•</span>
                        <span>Min ${r.minOrder}</span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          );
        }
        """);

        // Components: MenuViewer
        files.put("src/components/MenuViewer.tsx", """
        import React, { useState } from 'react';
        import { MenuItem, Restaurant } from '../types';

        interface MenuViewerProps {
          restaurant: Restaurant;
          menuItems: MenuItem[];
          onAddToCart: (item: MenuItem) => void;
          onBack: () => void;
        }

        export default function MenuViewer({ restaurant, menuItems, onAddToCart, onBack }: MenuViewerProps) {
          const [addedId, setAddedId] = useState<string | null>(null);

          const items = menuItems.filter(m => m.restaurantId === restaurant.id);

          const handleAdd = (item: MenuItem) => {
            onAddToCart(item);
            setAddedId(item.id);
            setTimeout(() => setAddedId(null), 1500);
          };

          return (
            <div className="space-y-6">
              {/* Restaurant Header */}
              <div className="glass-panel p-6 rounded-2xl border border-slate-800 flex items-center justify-between flex-wrap gap-4">
                <div className="flex items-center gap-4">
                  <button
                    onClick={onBack}
                    className="h-9 w-9 rounded-xl bg-slate-900 border border-slate-800 text-slate-300 hover:text-white flex items-center justify-center transition"
                  >
                    ←
                  </button>
                  <div className="text-3xl">{restaurant.imageEmoji}</div>
                  <div>
                    <h2 className="text-xl font-bold text-white">{restaurant.name}</h2>
                    <p className="text-xs text-slate-400">{restaurant.cuisine} · {restaurant.deliveryMinutes} mins delivery · ★ {restaurant.rating}</p>
                  </div>
                </div>
              </div>

              {/* Menu items grid */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {items.map(item => (
                  <div key={item.id} className="glass-panel p-5 rounded-2xl border border-slate-800 flex flex-col justify-between space-y-3">
                    <div>
                      <div className="flex items-start justify-between gap-2">
                        <h4 className="font-bold text-white text-sm">{item.name}</h4>
                        <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                          item.dietary === 'Veg' ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30' :
                          item.dietary === 'Chef Special' ? 'bg-amber-500/20 text-amber-300 border border-amber-500/30' :
                          'bg-slate-800 text-slate-300'
                        }`}>
                          {item.dietary}
                        </span>
                      </div>
                      <p className="text-xs text-slate-400 mt-1.5 leading-relaxed">{item.description}</p>
                    </div>

                    <div className="pt-2 flex items-center justify-between border-t border-slate-800/80">
                      <span className="font-mono font-bold text-amber-400 text-sm">${item.price.toFixed(2)}</span>
                      <button
                        onClick={() => handleAdd(item)}
                        className={`px-3 py-1.5 rounded-lg text-xs font-bold transition flex items-center gap-1 ${
                          addedId === item.id
                            ? 'bg-emerald-600 text-white'
                            : 'bg-amber-600 hover:bg-amber-500 text-white shadow-md shadow-amber-600/30'
                        }`}
                      >
                        {addedId === item.id ? '✓ Added' : '+ Add to Order'}
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          );
        }
        """);

        // Components: CartDrawer
        files.put("src/components/CartDrawer.tsx", """
        import React, { useState } from 'react';
        import { CartItem } from '../types';

        interface CartDrawerProps {
          items: CartItem[];
          onUpdateQuantity: (id: string, delta: number) => void;
          onClearCart: () => void;
          onPlaceOrder: (subtotal: number, deliveryFee: number, tax: number, total: number) => void;
          onClose: () => void;
        }

        export default function CartDrawer({ items, onUpdateQuantity, onPlaceOrder }: CartDrawerProps) {
          const [promoCode, setPromoCode] = useState('');
          const [discount, setDiscount] = useState(0);
          const [promoMsg, setPromoMsg] = useState('');

          const subtotal = items.reduce((sum, item) => sum + item.menuItem.price * item.quantity, 0);
          const deliveryFee = items.length > 0 ? 2.99 : 0;
          const tax = subtotal * 0.0825;
          const total = Math.max(0, subtotal + deliveryFee + tax - discount);

          const handleApplyPromo = (e: React.FormEvent) => {
            e.preventDefault();
            if (promoCode.trim().toUpperCase() === 'FEAST20') {
              setDiscount(subtotal * 0.2);
              setPromoMsg('✓ 20% discount applied!');
            } else {
              setPromoMsg('❌ Invalid promo code (try FEAST20)');
            }
          };

          if (items.length === 0) {
            return (
              <div className="glass-panel p-8 rounded-2xl text-center space-y-3">
                <span className="text-4xl">🛒</span>
                <h3 className="text-base font-bold text-white">Your Cart is Empty</h3>
                <p className="text-xs text-slate-400">Browse dishes from our restaurant catalog and add them to your order.</p>
              </div>
            );
          }

          return (
            <div className="glass-panel p-6 rounded-2xl border border-slate-800 space-y-6">
              <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                <h3 className="font-bold text-white text-base">Your Culinary Order ({items.length} items)</h3>
                <span className="text-xs text-amber-400 font-mono font-bold">${total.toFixed(2)} Total</span>
              </div>

              {/* Items List */}
              <div className="space-y-3 max-h-72 overflow-y-auto scroll-slim pr-1">
                {items.map(item => (
                  <div key={item.menuItem.id} className="p-3 bg-slate-900/60 rounded-xl border border-slate-800 flex items-center justify-between gap-3">
                    <div className="min-w-0 flex-1">
                      <h4 className="text-xs font-bold text-white truncate">{item.menuItem.name}</h4>
                      <p className="text-[11px] font-mono text-amber-400">${item.menuItem.price.toFixed(2)} each</p>
                    </div>

                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => onUpdateQuantity(item.menuItem.id, -1)}
                        className="w-6 h-6 rounded bg-slate-800 text-slate-300 hover:bg-slate-700 flex items-center justify-center font-bold text-xs"
                      >
                        -
                      </button>
                      <span className="text-xs font-mono font-bold text-white px-1.5">{item.quantity}</span>
                      <button
                        onClick={() => onUpdateQuantity(item.menuItem.id, 1)}
                        className="w-6 h-6 rounded bg-slate-800 text-slate-300 hover:bg-slate-700 flex items-center justify-center font-bold text-xs"
                      >
                        +
                      </button>
                    </div>
                  </div>
                ))}
              </div>

              {/* Promo code */}
              <form onSubmit={handleApplyPromo} className="flex gap-2">
                <input
                  type="text"
                  placeholder="Promo code (use FEAST20)"
                  value={promoCode}
                  onChange={e => setPromoCode(e.target.value)}
                  className="flex-1 bg-slate-900 border border-slate-800 rounded-lg px-3 py-1.5 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-amber-500 uppercase font-mono"
                />
                <button
                  type="submit"
                  className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-xs font-semibold text-slate-200 rounded-lg"
                >
                  Apply
                </button>
              </form>
              {promoMsg && <p className="text-[11px] text-amber-300 font-semibold">{promoMsg}</p>}

              {/* Cost summary breakdown */}
              <div className="space-y-1.5 text-xs border-t border-slate-800 pt-3">
                <div className="flex justify-between text-slate-400">
                  <span>Subtotal:</span>
                  <span className="font-mono text-slate-200">${subtotal.toFixed(2)}</span>
                </div>
                <div className="flex justify-between text-slate-400">
                  <span>Delivery Fee:</span>
                  <span className="font-mono text-slate-200">${deliveryFee.toFixed(2)}</span>
                </div>
                <div className="flex justify-between text-slate-400">
                  <span>Estimated Taxes (8.25%):</span>
                  <span className="font-mono text-slate-200">${tax.toFixed(2)}</span>
                </div>
                {discount > 0 && (
                  <div className="flex justify-between text-emerald-400">
                    <span>Discount:</span>
                    <span className="font-mono">-${discount.toFixed(2)}</span>
                  </div>
                )}
                <div className="flex justify-between text-white font-bold text-sm pt-2 border-t border-slate-800">
                  <span>Order Total:</span>
                  <span className="font-mono text-amber-400">${total.toFixed(2)}</span>
                </div>
              </div>

              <button
                onClick={() => onPlaceOrder(subtotal, deliveryFee, tax, total)}
                className="w-full py-2.5 rounded-xl text-xs font-bold bg-amber-600 hover:bg-amber-500 text-white shadow-lg shadow-amber-600/30 transition flex items-center justify-center gap-1.5"
              >
                <span>🚀 Place Simulated Delivery Order (${total.toFixed(2)})</span>
              </button>
            </div>
          );
        }
        """);

        // Components: OrderStatusTracker
        files.put("src/components/OrderStatusTracker.tsx", """
        import React from 'react';
        import { OrderState } from '../types';

        interface OrderStatusTrackerProps {
          order: OrderState | null;
          onNewOrder: () => void;
        }

        export default function OrderStatusTracker({ order, onNewOrder }: OrderStatusTrackerProps) {
          if (!order) {
            return (
              <div className="glass-panel p-8 rounded-2xl text-center space-y-3">
                <span className="text-4xl">🛵</span>
                <h3 className="text-base font-bold text-white">No Active Deliveries</h3>
                <p className="text-xs text-slate-400">Place an order to monitor real-time kitchen preparation and courier transit.</p>
              </div>
            );
          }

          const stages = [
            { id: 'RECEIVED', label: 'Order Confirmed', icon: '📝' },
            { id: 'PREPARING', label: 'Kitchen Preparing', icon: '🍳' },
            { id: 'ON_THE_WAY', label: 'Courier On Route', icon: '🛵' },
            { id: 'DELIVERED', label: 'Delivered', icon: '🎉' },
          ];

          return (
            <div className="space-y-6">
              <div className="border-b border-slate-800 pb-4">
                <h2 className="text-xl font-bold text-white flex items-center gap-2">
                  <span>🛵 Live Delivery Tracker</span>
                  <span className="text-xs px-2.5 py-0.5 rounded-full bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
                    Order #{order.id}
                  </span>
                </h2>
                <p className="text-xs text-slate-400 mt-1">
                  Real-time progression from partner kitchen to your destination.
                </p>
              </div>

              {/* Progress Stepper */}
              <div className="glass-panel p-6 rounded-2xl border border-slate-800 space-y-6">
                <div className="grid grid-cols-4 gap-2 text-center">
                  {stages.map((st, i) => (
                    <div key={st.id} className="space-y-2">
                      <div className={`w-10 h-10 mx-auto rounded-full flex items-center justify-center text-lg ${
                        i <= 2 ? 'bg-amber-500 text-slate-950 font-bold' : 'bg-slate-900 text-slate-500 border border-slate-800'
                      }`}>
                        {st.icon}
                      </div>
                      <span className={`text-[11px] font-semibold block ${i <= 2 ? 'text-amber-300' : 'text-slate-500'}`}>
                        {st.label}
                      </span>
                    </div>
                  ))}
                </div>

                {/* Courier info card */}
                <div className="bg-slate-900/60 p-4 rounded-xl border border-slate-800 flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-full bg-amber-500/10 border border-amber-500/30 flex items-center justify-center text-lg">
                      🧑‍🚀
                    </div>
                    <div>
                      <h4 className="text-xs font-bold text-white">Courier: Marcus V.</h4>
                      <p className="text-[10px] text-slate-400">Yamaha NMAX · 4.96 ★ (1,240 deliveries)</p>
                    </div>
                  </div>
                  <div className="text-right">
                    <span className="text-xs font-bold text-emerald-400">~14 mins away</span>
                    <span className="text-[10px] text-slate-500 block">Estimated Arrival</span>
                  </div>
                </div>

                <div className="pt-2 flex justify-end">
                  <button
                    onClick={onNewOrder}
                    className="px-4 py-2 rounded-xl text-xs font-semibold bg-slate-800 hover:bg-slate-700 text-slate-200"
                  >
                    Start Another Order
                  </button>
                </div>
              </div>
            </div>
          );
        }
        """);

        // Main App.tsx for Food Delivery
        files.put("src/App.tsx", """
        import React, { useState } from 'react';
        import { Restaurant, MenuItem, CartItem, OrderState } from './types';
        import { initialRestaurants, initialMenuItems } from './mockData';
        import RestaurantCatalog from './components/RestaurantCatalog';
        import MenuViewer from './components/MenuViewer';
        import CartDrawer from './components/CartDrawer';
        import OrderStatusTracker from './components/OrderStatusTracker';

        export default function App() {
          const [activeTab, setActiveTab] = useState<'catalog' | 'menu' | 'cart' | 'tracker'>('catalog');
          const [selectedRestaurant, setSelectedRestaurant] = useState<Restaurant>(initialRestaurants[0]);
          const [cartItems, setCartItems] = useState<CartItem[]>([]);
          const [activeOrder, setActiveOrder] = useState<OrderState | null>(null);

          const totalItemsCount = cartItems.reduce((sum, item) => sum + item.quantity, 0);

          const handleAddToCart = (menuItem: MenuItem) => {
            setCartItems(prev => {
              const existing = prev.find(i => i.menuItem.id === menuItem.id);
              if (existing) {
                return prev.map(i => i.menuItem.id === menuItem.id ? { ...i, quantity: i.quantity + 1 } : i);
              }
              return [...prev, { menuItem, quantity: 1 }];
            });
          };

          const handleUpdateQuantity = (id: string, delta: number) => {
            setCartItems(prev => {
              return prev.map(item => {
                if (item.menuItem.id === id) {
                  return { ...item, quantity: Math.max(0, item.quantity + delta) };
                }
                return item;
              }).filter(item => item.quantity > 0);
            });
          };

          const handlePlaceOrder = (subtotal: number, deliveryFee: number, tax: number, total: number) => {
            const newOrder: OrderState = {
              id: String(Math.floor(1000 + Math.random() * 9000)),
              items: [...cartItems],
              subtotal,
              deliveryFee,
              tax,
              total,
              status: 'ON_THE_WAY',
              estimatedArrivalMinutes: 20,
            };
            setActiveOrder(newOrder);
            setCartItems([]);
            setActiveTab('tracker');
          };

          return (
            <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans">
              <header className="border-b border-slate-800/80 bg-slate-900/60 backdrop-blur-md px-6 py-4 sticky top-0 z-30 flex flex-wrap items-center justify-between gap-4">
                <div className="flex items-center gap-3">
                  <div className="h-9 w-9 rounded-xl bg-gradient-to-tr from-amber-500 to-orange-400 flex items-center justify-center font-bold text-slate-950 shadow-md">
                    🍔
                  </div>
                  <div>
                    <h1 className="font-bold text-white text-base tracking-wide">%s</h1>
                    <span className="text-[10px] text-amber-400 font-mono">Curated Culinary Delivery</span>
                  </div>
                </div>

                <nav className="flex items-center gap-1 text-xs font-medium bg-slate-950 p-1 rounded-xl border border-slate-800">
                  <button
                    onClick={() => setActiveTab('catalog')}
                    className={`px-3 py-1.5 rounded-lg transition ${activeTab === 'catalog' ? 'bg-amber-600 text-white font-semibold' : 'text-slate-400 hover:text-white'}`}
                  >
                    🏪 Restaurants
                  </button>
                  <button
                    onClick={() => setActiveTab('cart')}
                    className={`px-3 py-1.5 rounded-lg transition flex items-center gap-1.5 ${activeTab === 'cart' ? 'bg-amber-600 text-white font-semibold' : 'text-slate-400 hover:text-white'}`}
                  >
                    <span>🛒 Cart</span>
                    {totalItemsCount > 0 && (
                      <span className="px-1.5 py-0.2 rounded-full bg-amber-400 text-slate-950 font-black text-[10px]">
                        {totalItemsCount}
                      </span>
                    )}
                  </button>
                  <button
                    onClick={() => setActiveTab('tracker')}
                    className={`px-3 py-1.5 rounded-lg transition ${activeTab === 'tracker' ? 'bg-amber-600 text-white font-semibold' : 'text-slate-400 hover:text-white'}`}
                  >
                    🛵 Live Tracker
                  </button>
                </nav>
              </header>

              <div className="bg-amber-950/40 border-b border-amber-500/20 px-6 py-2 text-[11px] text-amber-300 flex items-center justify-between">
                <span>🛡️ %s</span>
                <span className="font-mono text-amber-400/80">Active Delivery Sandbox</span>
              </div>

              <main className="flex-1 max-w-6xl w-full mx-auto px-4 py-8">
                {activeTab === 'catalog' && (
                  <RestaurantCatalog
                    restaurants={initialRestaurants}
                    onSelectRestaurant={r => {
                      setSelectedRestaurant(r);
                      setActiveTab('menu');
                    }}
                  />
                )}
                {activeTab === 'menu' && (
                  <MenuViewer
                    restaurant={selectedRestaurant}
                    menuItems={initialMenuItems}
                    onAddToCart={handleAddToCart}
                    onBack={() => setActiveTab('catalog')}
                  />
                )}
                {activeTab === 'cart' && (
                  <CartDrawer
                    items={cartItems}
                    onUpdateQuantity={handleUpdateQuantity}
                    onClearCart={() => setCartItems([])}
                    onPlaceOrder={handlePlaceOrder}
                    onClose={() => setActiveTab('catalog')}
                  />
                )}
                {activeTab === 'tracker' && (
                  <OrderStatusTracker
                    order={activeOrder}
                    onNewOrder={() => setActiveTab('catalog')}
                  />
                )}
              </main>

              <footer className="border-t border-slate-800/60 bg-slate-900/40 py-6 px-4 mt-auto">
                <div className="max-w-6xl mx-auto flex flex-col md:flex-row items-center justify-between gap-4 text-xs text-slate-500">
                  <p>© 2026 %s · Real runnable application generated in isolated workspace.</p>
                  <p className="flex items-center gap-1.5">
                    <span className="h-2 w-2 rounded-full bg-amber-400 inline-block" />
                    <span>Active Domain Engine: On-Demand Food Logistics</span>
                  </p>
                </div>
              </footer>
            </div>
          );
        }
        """.formatted(escapeJs(startup.getName()), escapeJs(spec.simulatedNotice()), escapeJs(startup.getName())));
    }

    // =========================================================================
    // 3. BIKE WORKSHOP MANAGEMENT SYNTHESIS
    // =========================================================================
    private void synthesizeBikeWorkshopApp(Startup startup, ProductSpec spec, Map<String, String> files) {
        // Types
        files.put("src/types.ts", """
        export type JobStage = 'RECEIVED' | 'INSPECTION' | 'PARTS_WAITING' | 'IN_REPAIR' | 'READY';

        export interface RepairJob {
          id: string;
          customerName: string;
          customerPhone: string;
          bikeModel: string;
          servicePackage: string;
          stage: JobStage;
          priority: 'URGENT' | 'HIGH' | 'NORMAL';
          estimatedCost: number;
          notes: string;
        }

        export interface SparePart {
          id: string;
          sku: string;
          name: string;
          category: string;
          stockQuantity: number;
          minThreshold: number;
          unitCost: number;
        }
        """);

        // Mock data
        files.put("src/mockData.ts", """
        import { RepairJob, SparePart } from './types';

        export const initialRepairJobs: RepairJob[] = [
          { id: 'JOB-301', customerName: 'Julian Hayes', customerPhone: '+1 (555) 234-8901', bikeModel: 'Specialized Tarmac SL7', servicePackage: 'Complete Overhaul', stage: 'IN_REPAIR', priority: 'URGENT', estimatedCost: 240, notes: 'Replace bottom bracket and bleed rear Shimano Di2 hydro brake.' },
          { id: 'JOB-302', customerName: 'Elena Rostova', customerPhone: '+1 (555) 789-1234', bikeModel: 'Trek Domane AL 3', servicePackage: 'Basic Tune-Up', stage: 'RECEIVED', priority: 'NORMAL', estimatedCost: 85, notes: 'Chain lubrication and front derailleur indexed shifting tune.' },
          { id: 'JOB-303', customerName: 'David Kim', customerPhone: '+1 (555) 456-6789', bikeModel: 'Santa Cruz Megatower', servicePackage: 'Suspension Service', stage: 'PARTS_WAITING', priority: 'HIGH', estimatedCost: 310, notes: 'Fox 38 damper seal kit backordered. Arriving tomorrow.' },
          { id: 'JOB-304', customerName: 'Sophie Bennett', customerPhone: '+1 (555) 321-7654', bikeModel: 'Brompton C Line Urban', servicePackage: 'Drivetrain Replacement', stage: 'READY', priority: 'NORMAL', estimatedCost: 145, notes: 'New 6-speed chain, rear sprockets, and tensioner fitted.' },
        ];

        export const initialSpareParts: SparePart[] = [
          { id: 'p-1', sku: 'SHIM-CN-M8100', name: 'Shimano XT 12-Speed Chain', category: 'Drivetrain', stockQuantity: 14, minThreshold: 5, unitCost: 42.00 },
          { id: 'p-2', sku: 'SRAM-BP-RSC', name: 'SRAM Code Metallic Brake Pads', category: 'Brakes', stockQuantity: 3, minThreshold: 6, unitCost: 28.50 },
          { id: 'p-3', sku: 'SCHW-TUBE-700', name: 'Schwalbe 700x28c Presta Tubes', category: 'Tires', stockQuantity: 28, minThreshold: 10, unitCost: 9.00 },
          { id: 'p-4', sku: 'PARK-CBL-11', name: 'Stainless Steel Shift Cable Inner', category: 'Cables', stockQuantity: 40, minThreshold: 15, unitCost: 4.50 },
          { id: 'p-5', sku: 'SHIM-CS-R8000', name: 'Ultegra 11-Speed Cassette 11-30', category: 'Drivetrain', stockQuantity: 2, minThreshold: 4, unitCost: 95.00 },
        ];
        """);

        // Components: JobKanbanBoard
        files.put("src/components/JobKanbanBoard.tsx", """
        import React from 'react';
        import { RepairJob, JobStage } from '../types';

        interface JobKanbanBoardProps {
          jobs: RepairJob[];
          onMoveStage: (jobId: string, nextStage: JobStage) => void;
          onOpenBooking: () => void;
        }

        export default function JobKanbanBoard({ jobs, onMoveStage, onOpenBooking }: JobKanbanBoardProps) {
          const columns: { stage: JobStage; label: string; icon: string }[] = [
            { stage: 'RECEIVED', label: 'Received Intake', icon: '📥' },
            { stage: 'INSPECTION', label: 'Diagnostic Check', icon: '🔍' },
            { stage: 'PARTS_WAITING', label: 'Parts Waiting', icon: '⏳' },
            { stage: 'IN_REPAIR', label: 'On Repair Stand', icon: '🛠️' },
            { stage: 'READY', label: 'Ready for Pickup', icon: '🚲' },
          ];

          const stageOrder: JobStage[] = ['RECEIVED', 'INSPECTION', 'PARTS_WAITING', 'IN_REPAIR', 'READY'];

          const handleAdvance = (job: RepairJob) => {
            const curIdx = stageOrder.indexOf(job.stage);
            if (curIdx < stageOrder.length - 1) {
              onMoveStage(job.id, stageOrder[curIdx + 1]);
            }
          };

          const handleRewind = (job: RepairJob) => {
            const curIdx = stageOrder.indexOf(job.stage);
            if (curIdx > 0) {
              onMoveStage(job.id, stageOrder[curIdx - 1]);
            }
          };

          return (
            <div className="space-y-6">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-800 pb-4">
                <div>
                  <h2 className="text-xl font-bold text-white flex items-center gap-2">
                    <span>🛠️ Workshop Service Kanban Board</span>
                    <span className="text-xs px-2.5 py-0.5 rounded-full bg-cyan-500/20 text-cyan-400 border border-cyan-500/30">
                      {jobs.length} Active Jobs
                    </span>
                  </h2>
                  <p className="text-xs text-slate-400 mt-1">
                    Track bicycle maintenance progression. Click arrows to transition ticket states.
                  </p>
                </div>
                <button
                  onClick={onOpenBooking}
                  className="px-4 py-2 rounded-xl text-xs font-bold bg-cyan-600 hover:bg-cyan-500 text-white shadow-lg shadow-cyan-600/30 transition flex items-center gap-1.5"
                >
                  <span>+ Book New Service</span>
                </button>
              </div>

              {/* Columns Grid */}
              <div className="grid grid-cols-1 md:grid-cols-5 gap-3">
                {columns.map(col => {
                  const colJobs = jobs.filter(j => j.stage === col.stage);
                  return (
                    <div key={col.stage} className="bg-slate-900/60 rounded-2xl border border-slate-800/80 p-3 flex flex-col min-h-[420px]">
                      <div className="flex items-center justify-between pb-2 mb-3 border-b border-slate-800 text-xs">
                        <span className="font-bold text-slate-200 flex items-center gap-1.5">
                          <span>{col.icon}</span>
                          <span>{col.label}</span>
                        </span>
                        <span className="px-1.5 py-0.2 rounded-full bg-slate-800 text-slate-400 font-mono text-[10px]">
                          {colJobs.length}
                        </span>
                      </div>

                      <div className="space-y-2.5 flex-1">
                        {colJobs.map(job => (
                          <div key={job.id} className="glass-panel p-3.5 rounded-xl border border-slate-700/80 space-y-2 hover:border-cyan-500/40 transition">
                            <div className="flex items-center justify-between gap-1 text-[10px]">
                              <span className="font-mono font-bold text-cyan-400">{job.id}</span>
                              <span className={`px-1.5 py-0.2 rounded font-bold uppercase ${
                                job.priority === 'URGENT' ? 'bg-rose-500/20 text-rose-300' :
                                job.priority === 'HIGH' ? 'bg-amber-500/20 text-amber-300' : 'bg-slate-800 text-slate-400'
                              }`}>
                                {job.priority}
                              </span>
                            </div>

                            <div>
                              <h4 className="text-xs font-bold text-white leading-tight">{job.bikeModel}</h4>
                              <p className="text-[11px] text-slate-400 mt-0.5">{job.customerName}</p>
                            </div>

                            <p className="text-[10px] text-slate-400 bg-slate-950/60 p-2 rounded-lg italic">
                              &quot;{job.notes}&quot;
                            </p>

                            <div className="flex items-center justify-between pt-1 border-t border-slate-800/80 text-[10px]">
                              <span className="font-mono text-cyan-300 font-bold">${job.estimatedCost}</span>
                              <div className="flex gap-1">
                                {col.stage !== 'RECEIVED' && (
                                  <button
                                    onClick={() => handleRewind(job)}
                                    title="Move back"
                                    className="px-2 py-0.5 rounded bg-slate-800 hover:bg-slate-700 text-slate-300"
                                  >
                                    ←
                                  </button>
                                )}
                                {col.stage !== 'READY' && (
                                  <button
                                    onClick={() => handleAdvance(job)}
                                    title="Advance stage"
                                    className="px-2 py-0.5 rounded bg-cyan-600 hover:bg-cyan-500 text-white font-bold"
                                  >
                                    →
                                  </button>
                                )}
                              </div>
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          );
        }
        """);

        // Components: ServiceBookingForm
        files.put("src/components/ServiceBookingForm.tsx", """
        import React, { useState } from 'react';
        import { RepairJob } from '../types';

        interface ServiceBookingFormProps {
          onAddJob: (job: RepairJob) => void;
          onCancel: () => void;
        }

        export default function ServiceBookingForm({ onAddJob, onCancel }: ServiceBookingFormProps) {
          const [customerName, setCustomerName] = useState('');
          const [customerPhone, setCustomerPhone] = useState('');
          const [bikeModel, setBikeModel] = useState('');
          const [servicePackage, setServicePackage] = useState('Comprehensive Overhaul ($180)');
          const [priority, setPriority] = useState<RepairJob['priority']>('NORMAL');
          const [notes, setNotes] = useState('');

          const handleSubmit = (e: React.FormEvent) => {
            e.preventDefault();
            if (!customerName.trim() || !bikeModel.trim()) return;

            const cost = servicePackage.includes('320') ? 320 : servicePackage.includes('180') ? 180 : 85;

            const newJob: RepairJob = {
              id: 'JOB-' + Math.floor(300 + Math.random() * 600),
              customerName: customerName.trim(),
              customerPhone: customerPhone.trim() || '+1 (555) 000-0000',
              bikeModel: bikeModel.trim(),
              servicePackage,
              stage: 'RECEIVED',
              priority,
              estimatedCost: cost,
              notes: notes.trim() || 'Standard diagnostic intake.',
            };

            onAddJob(newJob);
          };

          return (
            <div className="max-w-2xl mx-auto glass-panel p-6 sm:p-8 rounded-2xl border border-cyan-500/30 space-y-6 animate-fade-in">
              <div className="border-b border-slate-800 pb-3">
                <h3 className="text-lg font-bold text-white">📝 Book Inbound Bicycle Repair Job</h3>
                <p className="text-xs text-slate-400 mt-1">Generate a workshop service ticket with customer details and bike specs.</p>
              </div>

              <form onSubmit={handleSubmit} className="space-y-4">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label className="text-xs font-semibold text-slate-300 block mb-1">Customer Full Name</label>
                    <input
                      type="text"
                      required
                      placeholder="e.g. Marcus Vance"
                      value={customerName}
                      onChange={e => setCustomerName(e.target.value)}
                      className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3 py-2 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-cyan-500"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-semibold text-slate-300 block mb-1">Contact Phone</label>
                    <input
                      type="text"
                      placeholder="+1 (555) 012-3456"
                      value={customerPhone}
                      onChange={e => setCustomerPhone(e.target.value)}
                      className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3 py-2 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-cyan-500"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label className="text-xs font-semibold text-slate-300 block mb-1">Bicycle Brand & Model</label>
                    <input
                      type="text"
                      required
                      placeholder="e.g. Cannondale SuperSix EVO"
                      value={bikeModel}
                      onChange={e => setBikeModel(e.target.value)}
                      className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3 py-2 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-cyan-500"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-semibold text-slate-300 block mb-1">Service Tier Package</label>
                    <select
                      value={servicePackage}
                      onChange={e => setServicePackage(e.target.value)}
                      className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3 py-2 text-xs text-white focus:outline-none focus:border-cyan-500"
                    >
                      <option>Basic Safety Tune-Up ($85)</option>
                      <option>Comprehensive Overhaul ($180)</option>
                      <option>Pro Race Suspension & Drivetrain ($320)</option>
                    </select>
                  </div>
                </div>

                <div>
                  <label className="text-xs font-semibold text-slate-300 block mb-1">Job Priority</label>
                  <div className="flex gap-2">
                    {(['NORMAL', 'HIGH', 'URGENT'] as RepairJob['priority'][]).map(p => (
                      <button
                        type="button"
                        key={p}
                        onClick={() => setPriority(p)}
                        className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
                          priority === p ? 'bg-cyan-600 text-white' : 'bg-slate-900 border border-slate-800 text-slate-400'
                        }`}
                      >
                        {p}
                      </button>
                    ))}
                  </div>
                </div>

                <div>
                  <label className="text-xs font-semibold text-slate-300 block mb-1">Mechanic Intake Notes & Fault Description</label>
                  <textarea
                    rows={3}
                    placeholder="Describe reported creaking, shifting hesitations, or brake fade..."
                    value={notes}
                    onChange={e => setNotes(e.target.value)}
                    className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3 py-2 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-cyan-500"
                  />
                </div>

                <div className="pt-2 flex justify-end gap-3 border-t border-slate-800">
                  <button
                    type="button"
                    onClick={onCancel}
                    className="px-4 py-2 rounded-xl text-xs font-semibold bg-slate-800 text-slate-300 hover:text-white"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="px-5 py-2 rounded-xl text-xs font-bold bg-cyan-600 hover:bg-cyan-500 text-white shadow-md shadow-cyan-600/30"
                  >
                    Save & Create Ticket
                  </button>
                </div>
              </form>
            </div>
          );
        }
        """);

        // Components: InventoryManager
        files.put("src/components/InventoryManager.tsx", """
        import React, { useState } from 'react';
        import { SparePart } from '../types';

        interface InventoryManagerProps {
          parts: SparePart[];
          onRestock: (partId: string, amount: number) => void;
        }

        export default function InventoryManager({ parts, onRestock }: InventoryManagerProps) {
          const [search, setSearch] = useState('');

          const filtered = parts.filter(p =>
            p.name.toLowerCase().includes(search.toLowerCase()) || p.sku.toLowerCase().includes(search.toLowerCase())
          );

          return (
            <div className="space-y-6">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-800 pb-4">
                <div>
                  <h2 className="text-xl font-bold text-white flex items-center gap-2">
                    <span>⚙️ Workshop Spare Parts Inventory</span>
                    <span className="text-xs px-2.5 py-0.5 rounded-full bg-cyan-500/20 text-cyan-400 border border-cyan-500/30">
                      {parts.length} Tracked SKUs
                    </span>
                  </h2>
                  <p className="text-xs text-slate-400 mt-1">
                    Manage chains, cassettes, brake pads, and hydraulic fluids. One-click restock adds +5 units.
                  </p>
                </div>
              </div>

              <input
                type="text"
                placeholder="Search by part name or SKU..."
                value={search}
                onChange={e => setSearch(e.target.value)}
                className="w-full bg-slate-900 border border-slate-800 rounded-xl px-4 py-2.5 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-cyan-500"
              />

              <div className="glass-panel rounded-2xl border border-slate-800 overflow-hidden">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-900/80 border-b border-slate-800 text-[11px] uppercase font-bold text-slate-400">
                    <tr>
                      <th className="p-3.5">SKU & Component</th>
                      <th className="p-3.5">Category</th>
                      <th className="p-3.5">Stock Level</th>
                      <th className="p-3.5">Unit Cost</th>
                      <th className="p-3.5 text-right">Action</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/60">
                    {filtered.map(part => {
                      const isLow = part.stockQuantity <= part.minThreshold;
                      return (
                        <tr key={part.id} className="hover:bg-slate-900/40 transition">
                          <td className="p-3.5">
                            <span className="font-bold text-white block">{part.name}</span>
                            <span className="text-[10px] font-mono text-cyan-400">{part.sku}</span>
                          </td>
                          <td className="p-3.5 text-slate-300">{part.category}</td>
                          <td className="p-3.5">
                            <span className={`px-2 py-0.5 rounded text-[11px] font-bold ${
                              isLow ? 'bg-rose-500/20 text-rose-300 border border-rose-500/30' : 'bg-slate-800 text-slate-200'
                            }`}>
                              {part.stockQuantity} in stock {isLow && '⚠️ LOW'}
                            </span>
                          </td>
                          <td className="p-3.5 font-mono text-slate-200">${part.unitCost.toFixed(2)}</td>
                          <td className="p-3.5 text-right">
                            <button
                              onClick={() => onRestock(part.id, 5)}
                              className="px-2.5 py-1 rounded-lg text-[11px] font-semibold bg-slate-800 hover:bg-cyan-600 hover:text-white text-slate-300 transition"
                            >
                              +5 Restock
                            </button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          );
        }
        """);

        // Main App.tsx for Bike Workshop
        files.put("src/App.tsx", """
        import React, { useState, useEffect } from 'react';
        import { RepairJob, SparePart, JobStage } from './types';
        import { initialRepairJobs, initialSpareParts } from './mockData';
        import JobKanbanBoard from './components/JobKanbanBoard';
        import ServiceBookingForm from './components/ServiceBookingForm';
        import InventoryManager from './components/InventoryManager';

        export default function App() {
          const [activeTab, setActiveTab] = useState<'kanban' | 'booking' | 'inventory'>('kanban');
          const [jobs, setJobs] = useState<RepairJob[]>(() => {
            const saved = localStorage.getItem('bike_workshop_jobs');
            return saved ? JSON.parse(saved) : initialRepairJobs;
          });
          const [parts, setParts] = useState<SparePart[]>(() => {
            const saved = localStorage.getItem('bike_workshop_parts');
            return saved ? JSON.parse(saved) : initialSpareParts;
          });

          useEffect(() => {
            localStorage.setItem('bike_workshop_jobs', JSON.stringify(jobs));
          }, [jobs]);

          useEffect(() => {
            localStorage.setItem('bike_workshop_parts', JSON.stringify(parts));
          }, [parts]);

          const handleMoveStage = (jobId: string, nextStage: JobStage) => {
            setJobs(prev => prev.map(j => j.id === jobId ? { ...j, stage: nextStage } : j));
          };

          const handleAddJob = (job: RepairJob) => {
            setJobs(prev => [job, ...prev]);
            setActiveTab('kanban');
          };

          const handleRestock = (partId: string, amount: number) => {
            setParts(prev => prev.map(p => p.id === partId ? { ...p, stockQuantity: p.stockQuantity + amount } : p));
          };

          return (
            <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans">
              <header className="border-b border-slate-800/80 bg-slate-900/60 backdrop-blur-md px-6 py-4 sticky top-0 z-30 flex flex-wrap items-center justify-between gap-4">
                <div className="flex items-center gap-3">
                  <div className="h-9 w-9 rounded-xl bg-gradient-to-tr from-cyan-500 to-blue-500 flex items-center justify-center font-bold text-slate-950 shadow-md">
                    🚲
                  </div>
                  <div>
                    <h1 className="font-bold text-white text-base tracking-wide">%s</h1>
                    <span className="text-[10px] text-cyan-400 font-mono">Bicycle Workshop Operations</span>
                  </div>
                </div>

                <nav className="flex items-center gap-1 text-xs font-medium bg-slate-950 p-1 rounded-xl border border-slate-800">
                  <button
                    onClick={() => setActiveTab('kanban')}
                    className={`px-3 py-1.5 rounded-lg transition ${activeTab === 'kanban' ? 'bg-cyan-600 text-white font-semibold' : 'text-slate-400 hover:text-white'}`}
                  >
                    🛠️ Service Kanban
                  </button>
                  <button
                    onClick={() => setActiveTab('booking')}
                    className={`px-3 py-1.5 rounded-lg transition ${activeTab === 'booking' ? 'bg-cyan-600 text-white font-semibold' : 'text-slate-400 hover:text-white'}`}
                  >
                    📝 Book Service
                  </button>
                  <button
                    onClick={() => setActiveTab('inventory')}
                    className={`px-3 py-1.5 rounded-lg transition ${activeTab === 'inventory' ? 'bg-cyan-600 text-white font-semibold' : 'text-slate-400 hover:text-white'}`}
                  >
                    ⚙️ Parts Inventory
                  </button>
                </nav>
              </header>

              <div className="bg-cyan-950/40 border-b border-cyan-500/20 px-6 py-2 text-[11px] text-cyan-300 flex items-center justify-between">
                <span>🛡️ %s</span>
                <span className="font-mono text-cyan-400/80">Local Storage Sync</span>
              </div>

              <main className="flex-1 max-w-7xl w-full mx-auto px-4 py-8">
                {activeTab === 'kanban' && (
                  <JobKanbanBoard
                    jobs={jobs}
                    onMoveStage={handleMoveStage}
                    onOpenBooking={() => setActiveTab('booking')}
                  />
                )}
                {activeTab === 'booking' && (
                  <ServiceBookingForm
                    onAddJob={handleAddJob}
                    onCancel={() => setActiveTab('kanban')}
                  />
                )}
                {activeTab === 'inventory' && (
                  <InventoryManager
                    parts={parts}
                    onRestock={handleRestock}
                  />
                )}
              </main>

              <footer className="border-t border-slate-800/60 bg-slate-900/40 py-6 px-4 mt-auto">
                <div className="max-w-7xl mx-auto flex flex-col md:flex-row items-center justify-between gap-4 text-xs text-slate-500">
                  <p>© 2026 %s · Real runnable application generated in isolated workspace.</p>
                  <p className="flex items-center gap-1.5">
                    <span className="h-2 w-2 rounded-full bg-cyan-400 inline-block" />
                    <span>Active Domain Engine: Bike Workshop Operations</span>
                  </p>
                </div>
              </footer>
            </div>
          );
        }
        """.formatted(escapeJs(startup.getName()), escapeJs(spec.simulatedNotice()), escapeJs(startup.getName())));
    }

    // =========================================================================
    // 4. PROJECT MANAGEMENT / KANBAN SAAS
    // =========================================================================
    private void synthesizeProjectManagementApp(Startup startup, ProductSpec spec, Map<String, String> files) {
        files.put("src/types.ts", """
        export type TaskStatus = 'BACKLOG' | 'IN_PROGRESS' | 'REVIEW' | 'DONE';

        export interface Task {
          id: string;
          title: string;
          assignee: string;
          points: number;
          priority: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
          status: TaskStatus;
        }
        """);

        files.put("src/mockData.ts", """
        import { Task } from './types';

        export const initialTasks: Task[] = [
          { id: 'TSK-101', title: 'Implement Redis Session Cache Cluster', assignee: 'Alex R.', points: 5, priority: 'HIGH', status: 'IN_PROGRESS' },
          { id: 'TSK-102', title: 'PostgreSQL Zero-Downtime Migration Script', assignee: 'Dev Lead', points: 8, priority: 'CRITICAL', status: 'BACKLOG' },
          { id: 'TSK-103', title: 'OAuth2 Multi-Tenant Organization Scope', assignee: 'Sarah C.', points: 3, priority: 'MEDIUM', status: 'REVIEW' },
          { id: 'TSK-104', title: 'Telemetry Prometheus Metric Exporter', assignee: 'Marcus V.', points: 2, priority: 'LOW', status: 'DONE' },
        ];
        """);

        files.put("src/App.tsx", """
        import React, { useState } from 'react';
        import { Task, TaskStatus } from './types';
        import { initialTasks } from './mockData';

        export default function App() {
          const [tasks, setTasks] = useState<Task[]>(initialTasks);
          const [filter, setFilter] = useState('');

          const columns: { status: TaskStatus; label: string }[] = [
            { status: 'BACKLOG', label: 'Backlog' },
            { status: 'IN_PROGRESS', label: 'In Progress' },
            { status: 'REVIEW', label: 'Review' },
            { status: 'DONE', label: 'Completed' },
          ];

          const advance = (id: string) => {
            const flow: TaskStatus[] = ['BACKLOG', 'IN_PROGRESS', 'REVIEW', 'DONE'];
            setTasks(prev => prev.map(t => {
              if (t.id === id) {
                const idx = flow.indexOf(t.status);
                if (idx < flow.length - 1) return { ...t, status: flow[idx + 1] };
              }
              return t;
            }));
          };

          return (
            <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans">
              <header className="border-b border-slate-800 bg-slate-900/60 p-4 sticky top-0 z-20 flex justify-between items-center">
                <div className="flex items-center gap-2">
                  <span className="text-xl">📋</span>
                  <h1 className="font-bold text-white">%s</h1>
                </div>
                <input
                  type="text"
                  placeholder="Filter tasks..."
                  value={filter}
                  onChange={e => setFilter(e.target.value)}
                  className="bg-slate-900 border border-slate-800 rounded-lg px-3 py-1.5 text-xs text-white"
                />
              </header>

              <main className="flex-1 max-w-6xl w-full mx-auto p-6 space-y-6">
                <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                  {columns.map(col => (
                    <div key={col.status} className="bg-slate-900/40 p-4 rounded-xl border border-slate-800 min-h-[350px]">
                      <h3 className="font-bold text-xs uppercase tracking-wider text-indigo-400 mb-3">{col.label}</h3>
                      <div className="space-y-2">
                        {tasks.filter(t => t.status === col.status && t.title.toLowerCase().includes(filter.toLowerCase())).map(t => (
                          <div key={t.id} className="glass-panel p-3 rounded-lg border border-slate-800 space-y-2">
                            <span className="text-[10px] font-mono text-slate-400">{t.id} · {t.assignee}</span>
                            <h4 className="text-xs font-semibold text-white">{t.title}</h4>
                            <div className="flex justify-between items-center pt-1 text-[10px]">
                              <span className="text-indigo-400 font-bold">{t.points} pts</span>
                              {col.status !== 'DONE' && (
                                <button
                                  onClick={() => advance(t.id)}
                                  className="px-2 py-0.5 rounded bg-indigo-600 text-white font-bold"
                                >
                                  Advance →
                                </button>
                              )}
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              </main>
            </div>
          );
        }
        """.formatted(escapeJs(startup.getName())));
    }

    // =========================================================================
    // 5. CLEAN ENERGY / TELEMETRY
    // =========================================================================
    private void synthesizeCleanEnergyApp(Startup startup, ProductSpec spec, Map<String, String> files) {
        files.put("src/types.ts", """
        export interface Telemetry {
          batterySoc: number;
          demandKw: number;
          tariffRate: number;
          autoShave: boolean;
        }
        """);

        files.put("src/App.tsx", """
        import React, { useState } from 'react';

        export default function App() {
          const [soc, setSoc] = useState(82);
          const [autoShave, setAutoShave] = useState(true);
          const [demandKw, setDemandKw] = useState(485);

          const tariff = 0.38;
          const savings = Math.round(demandKw * tariff * (autoShave ? 1.4 : 0.8));

          return (
            <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans">
              <header className="border-b border-slate-800 bg-slate-900/60 p-4 sticky top-0 z-20 flex justify-between items-center">
                <div className="flex items-center gap-2">
                  <span className="text-xl">⚡</span>
                  <h1 className="font-bold text-white">%s</h1>
                </div>
                <span className="px-2.5 py-0.5 rounded-full text-xs font-mono font-bold bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                  GRID ACTIVE
                </span>
              </header>

              <main className="flex-1 max-w-5xl w-full mx-auto p-6 space-y-6">
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  <div className="glass-panel p-5 rounded-xl border border-slate-800">
                    <span className="text-xs text-slate-400">Battery Storage (SoC)</span>
                    <h3 className="text-2xl font-black text-emerald-400 font-mono mt-1">{soc}%%</h3>
                    <input
                      type="range"
                      min="20"
                      max="100"
                      value={soc}
                      onChange={e => setSoc(Number(e.target.value))}
                      className="w-full accent-emerald-500 mt-2"
                    />
                  </div>
                  <div className="glass-panel p-5 rounded-xl border border-slate-800">
                    <span className="text-xs text-slate-400">Demand Load</span>
                    <h3 className="text-2xl font-black text-sky-400 font-mono mt-1">{demandKw} kW</h3>
                    <input
                      type="range"
                      min="100"
                      max="800"
                      value={demandKw}
                      onChange={e => setDemandKw(Number(e.target.value))}
                      className="w-full accent-sky-500 mt-2"
                    />
                  </div>
                  <div className="glass-panel p-5 rounded-xl border border-slate-800">
                    <span className="text-xs text-slate-400">Avoided Peak Surcharges</span>
                    <h3 className="text-2xl font-black text-amber-400 font-mono mt-1">${savings} / day</h3>
                    <button
                      onClick={() => setAutoShave(!autoShave)}
                      className="mt-2 text-xs font-semibold px-2.5 py-1 rounded bg-slate-800 text-slate-200"
                    >
                      Peak Shaving: {autoShave ? 'ACTIVE' : 'MANUAL'}
                    </button>
                  </div>
                </div>
              </main>
            </div>
          );
        }
        """.formatted(escapeJs(startup.getName())));
    }

    // =========================================================================
    // 6. GENERAL / CUSTOM VENTURE SYNTHESIS
    // =========================================================================
    // DESCRIPTION-DRIVEN CUSTOM VENTURE SYNTHESIS (Multi-Screen, Responsive)
    // =========================================================================
    private void synthesizeCustomApp(Startup startup, ProductSpec spec, Map<String, String> files) {
        String appName = escapeJs(startup.getName());
        String category = escapeJs(spec.category());
        String tagline = escapeJs(spec.tagline());
        String problem = escapeJs(startup.getProblem() != null ? startup.getProblem() : "Operational bottlenecks and workflow fragmentation");
        String solution = escapeJs(startup.getSolution() != null ? startup.getSolution() : spec.summary());

        // 1. types.ts
        files.put("src/types.ts", """
        export type DomainStatus = 'ACTIVE' | 'PENDING' | 'RESOLVED' | 'REVIEW';
        export type DomainPriority = 'URGENT' | 'HIGH' | 'NORMAL' | 'LOW';

        export interface DomainRecord {
          id: string;
          title: string;
          category: string;
          status: DomainStatus;
          priority: DomainPriority;
          description: string;
          assignee: string;
          updatedAt: string;
        }

        export interface MetricCardData {
          label: string;
          value: string;
          change: string;
          isPositive: boolean;
          subtext: string;
        }

        export interface SystemActivity {
          id: string;
          action: string;
          actor: string;
          timeAgo: string;
        }
        """);

        // 2. mockData.ts
        files.put("src/mockData.ts", """
        import { DomainRecord, MetricCardData, SystemActivity } from './types';

        export const initialDomainRecords: DomainRecord[] = [
          {
            id: 'REC-101',
            title: '%s Core Pipeline Setup',
            category: 'Operations',
            status: 'ACTIVE',
            priority: 'HIGH',
            description: '%s',
            assignee: 'Lead Specialist',
            updatedAt: '10m ago'
          },
          {
            id: 'REC-102',
            title: 'Initial Intake Verification',
            category: 'Onboarding',
            status: 'RESOLVED',
            priority: 'NORMAL',
            description: '%s',
            assignee: 'Operations Team',
            updatedAt: '35m ago'
          },
          {
            id: 'REC-103',
            title: 'Service Protocol Calibration',
            category: 'Compliance',
            status: 'PENDING',
            priority: 'URGENT',
            description: 'Automated validation check against domain requirements',
            assignee: 'Quality Lead',
            updatedAt: '1h ago'
          },
          {
            id: 'REC-104',
            title: 'Customer Workflow Dispatch',
            category: 'Delivery',
            status: 'ACTIVE',
            priority: 'NORMAL',
            description: 'Synchronous dispatch across verified service endpoints',
            assignee: 'Support Lead',
            updatedAt: '2h ago'
          }
        ];

        export const initialMetrics: MetricCardData[] = [
          { label: 'Active Pipeline', value: '42', change: '+14%%', isPositive: true, subtext: 'vs previous week' },
          { label: 'Operational SLA', value: '99.4%%', change: '+0.6%%', isPositive: true, subtext: 'within threshold' },
          { label: 'Avg Cycle Time', value: '18m', change: '-22%%', isPositive: true, subtext: 'faster turnaround' },
          { label: 'Satisfaction Rate', value: '98.2%%', change: '+3.1%%', isPositive: true, subtext: 'customer verified' }
        ];

        export const initialActivities: SystemActivity[] = [
          { id: 'ACT-01', action: 'Approved operational record REC-102', actor: 'Automated Policy Engine', timeAgo: '35m ago' },
          { id: 'ACT-02', action: 'Assigned REC-103 priority escalation', actor: 'Operations Lead', timeAgo: '1h ago' },
          { id: 'ACT-03', action: 'Synchronized live workspace state', actor: 'Simulator Engine', timeAgo: '2h ago' }
        ];
        """.formatted(appName, solution, problem));

        // 3. components/DashboardOverview.tsx
        files.put("src/components/DashboardOverview.tsx", """
        import { DomainRecord, MetricCardData, SystemActivity } from '../types';

        interface DashboardOverviewProps {
          records: DomainRecord[];
          metrics: MetricCardData[];
          activities: SystemActivity[];
          onNavigate: (screen: 'RECORDS' | 'CREATE' | 'ANALYTICS') => void;
        }

        export default function DashboardOverview({
          records,
          metrics,
          activities,
          onNavigate
        }: DashboardOverviewProps) {
          const activeCount = records.filter(r => r.status === 'ACTIVE').length;
          const urgentCount = records.filter(r => r.priority === 'URGENT').length;

          return (
            <div className="space-y-6 animate-fade-in">
              {/* Top KPI Cards Grid */}
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                {metrics.map((m, idx) => (
                  <div key={idx} className="bg-slate-900/80 border border-slate-800 rounded-2xl p-5 shadow-lg flex flex-col justify-between">
                    <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">{m.label}</span>
                    <div className="flex items-baseline justify-between mt-3">
                      <span className="text-3xl font-black text-white">{m.value}</span>
                      <span className={`text-xs font-bold px-2 py-0.5 rounded-full ${m.isPositive ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30' : 'bg-rose-500/20 text-rose-400'}`}>
                        {m.change}
                      </span>
                    </div>
                    <span className="text-[11px] text-slate-400 mt-2">{m.subtext}</span>
                  </div>
                ))}
              </div>

              {/* Action Banner */}
              <div className="bg-gradient-to-r from-indigo-950/60 via-slate-900 to-slate-950 border border-indigo-500/30 rounded-2xl p-6 shadow-xl flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
                <div>
                  <h3 className="text-lg font-bold text-white flex items-center gap-2">
                    <span>⚡</span> Operational Highlights
                  </h3>
                  <p className="text-xs text-slate-400 mt-1">
                    Currently tracking {records.length} records in this workspace ({activeCount} active, {urgentCount} urgent).
                  </p>
                </div>
                <div className="flex items-center gap-2.5">
                  <button
                    onClick={() => onNavigate('CREATE')}
                    className="px-4 py-2 rounded-xl text-xs font-bold bg-indigo-600 hover:bg-indigo-500 text-white shadow-lg transition flex items-center gap-1.5"
                  >
                    <span>➕</span> New Record
                  </button>
                  <button
                    onClick={() => onNavigate('RECORDS')}
                    className="px-4 py-2 rounded-xl text-xs font-semibold bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 transition"
                  >
                    View All Records
                  </button>
                </div>
              </div>

              {/* Bottom 2-Column: Live Records Preview + Activity Stream */}
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                <div className="lg:col-span-2 bg-slate-900/60 border border-slate-800 rounded-2xl p-5 shadow-lg">
                  <div className="flex items-center justify-between mb-4">
                    <h4 className="text-sm font-bold text-white flex items-center gap-2">
                      <span>📋</span> Active Queue
                    </h4>
                    <button
                      onClick={() => onNavigate('RECORDS')}
                      className="text-xs text-indigo-400 hover:text-indigo-300 font-medium"
                    >
                      Inspect All →
                    </button>
                  </div>
                  <div className="space-y-2.5">
                    {records.slice(0, 4).map(r => (
                      <div key={r.id} className="p-3.5 rounded-xl bg-slate-950/60 border border-slate-800/80 hover:border-slate-700 transition flex items-center justify-between gap-3">
                        <div className="min-w-0">
                          <div className="flex items-center gap-2">
                            <span className="text-[10px] font-mono text-indigo-400 font-bold">{r.id}</span>
                            <span className="text-xs font-semibold text-white truncate">{r.title}</span>
                          </div>
                          <p className="text-[11px] text-slate-400 truncate mt-0.5">{r.description}</p>
                        </div>
                        <div className="flex items-center gap-2 shrink-0">
                          <span className={`text-[10px] font-bold px-2 py-0.5 rounded ${
                            r.priority === 'URGENT' ? 'bg-rose-500/20 text-rose-300 border border-rose-500/40' :
                            r.priority === 'HIGH' ? 'bg-amber-500/20 text-amber-300 border border-amber-500/40' :
                            'bg-slate-800 text-slate-300'
                          }`}>
                            {r.priority}
                          </span>
                          <span className="text-[10px] font-semibold px-2 py-0.5 rounded bg-slate-800 text-indigo-300">
                            {r.status}
                          </span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-5 shadow-lg">
                  <h4 className="text-sm font-bold text-white mb-4 flex items-center gap-2">
                    <span>📡</span> Activity Stream
                  </h4>
                  <div className="space-y-3">
                    {activities.map(a => (
                      <div key={a.id} className="text-xs border-l-2 border-indigo-500/50 pl-3 py-1">
                        <p className="text-slate-200 font-medium">{a.action}</p>
                        <div className="flex justify-between items-center text-[10px] text-slate-500 mt-1">
                          <span>{a.actor}</span>
                          <span>{a.timeAgo}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          );
        }
        """);

        // 4. components/ItemManager.tsx
        files.put("src/components/ItemManager.tsx", """
        import { useState } from 'react';
        import { DomainRecord, DomainStatus } from '../types';

        interface ItemManagerProps {
          records: DomainRecord[];
          onStatusChange: (id: string, newStatus: DomainStatus) => void;
          onOpenCreate: () => void;
        }

        export default function ItemManager({ records, onStatusChange, onOpenCreate }: ItemManagerProps) {
          const [search, setSearch] = useState('');
          const [statusFilter, setStatusFilter] = useState<'ALL' | DomainStatus>('ALL');

          const filtered = records.filter(r => {
            const matchesSearch = r.title.toLowerCase().includes(search.toLowerCase()) ||
                                  r.id.toLowerCase().includes(search.toLowerCase()) ||
                                  r.description.toLowerCase().includes(search.toLowerCase());
            const matchesStatus = statusFilter === 'ALL' || r.status === statusFilter;
            return matchesSearch && matchesStatus;
          });

          return (
            <div className="space-y-4 animate-fade-in">
              {/* Header Bar */}
              <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3 bg-slate-900/60 border border-slate-800 p-4 rounded-2xl">
                <div className="flex items-center gap-2 flex-wrap">
                  {(['ALL', 'ACTIVE', 'PENDING', 'RESOLVED'] as const).map(st => (
                    <button
                      key={st}
                      onClick={() => setStatusFilter(st)}
                      className={`px-3 py-1 rounded-lg text-xs font-bold transition ${
                        statusFilter === st
                          ? 'bg-indigo-600 text-white shadow'
                          : 'bg-slate-800 text-slate-400 hover:text-white'
                      }`}
                    >
                      {st} {st === 'ALL' ? `(${records.length})` : `(${records.filter(r => r.status === st).length})`}
                    </button>
                  ))}
                </div>

                <div className="flex items-center gap-2 w-full sm:w-auto">
                  <input
                    type="text"
                    placeholder="Search records by ID, title, details..."
                    value={search}
                    onChange={e => setSearch(e.target.value)}
                    className="bg-slate-950 border border-slate-800 rounded-xl px-3 py-1.5 text-xs text-white placeholder-slate-500 w-full sm:w-64 focus:outline-none focus:border-indigo-500"
                  />
                  <button
                    onClick={onOpenCreate}
                    className="px-3.5 py-1.5 rounded-xl text-xs font-bold bg-indigo-600 hover:bg-indigo-500 text-white whitespace-nowrap shadow transition"
                  >
                    + Add
                  </button>
                </div>
              </div>

              {/* Records List / Grid */}
              {filtered.length === 0 ? (
                <div className="bg-slate-900/40 border border-slate-800 rounded-2xl p-12 text-center text-slate-400">
                  <p className="text-sm font-semibold">No records match the active criteria.</p>
                  <button
                    onClick={() => { setSearch(''); setStatusFilter('ALL'); }}
                    className="mt-3 text-xs text-indigo-400 hover:underline"
                  >
                    Clear Filters
                  </button>
                </div>
              ) : (
                <div className="space-y-3">
                  {filtered.map(r => (
                    <div
                      key={r.id}
                      className="bg-slate-900/70 border border-slate-800 hover:border-slate-700 p-4 rounded-2xl transition shadow-sm flex flex-col md:flex-row items-start md:items-center justify-between gap-4"
                    >
                      <div className="space-y-1 max-w-xl">
                        <div className="flex items-center gap-2.5 flex-wrap">
                          <span className="font-mono text-xs font-bold text-indigo-400 bg-indigo-950/60 px-2 py-0.5 rounded border border-indigo-500/30">
                            {r.id}
                          </span>
                          <h4 className="text-sm font-bold text-white">{r.title}</h4>
                          <span className={`text-[10px] font-bold px-2 py-0.5 rounded ${
                            r.priority === 'URGENT' ? 'bg-rose-500/20 text-rose-300 border border-rose-500/40' :
                            r.priority === 'HIGH' ? 'bg-amber-500/20 text-amber-300' :
                            'bg-slate-800 text-slate-300'
                          }`}>
                            {r.priority}
                          </span>
                        </div>
                        <p className="text-xs text-slate-300">{r.description}</p>
                        <div className="flex items-center gap-3 text-[11px] text-slate-400 pt-1">
                          <span>📁 {r.category}</span>
                          <span>👤 {r.assignee}</span>
                          <span>🕒 {r.updatedAt}</span>
                        </div>
                      </div>

                      <div className="flex items-center gap-2 self-end md:self-center shrink-0">
                        {r.status === 'PENDING' && (
                          <button
                            onClick={() => onStatusChange(r.id, 'ACTIVE')}
                            className="px-3 py-1 text-xs font-semibold rounded-lg bg-emerald-600/30 text-emerald-300 border border-emerald-500/40 hover:bg-emerald-600/50 transition"
                          >
                            ▶ Activate
                          </button>
                        )}
                        {r.status === 'ACTIVE' && (
                          <button
                            onClick={() => onStatusChange(r.id, 'RESOLVED')}
                            className="px-3 py-1 text-xs font-semibold rounded-lg bg-indigo-600/30 text-indigo-300 border border-indigo-500/40 hover:bg-indigo-600/50 transition"
                          >
                            ✔ Resolve
                          </button>
                        )}
                        {r.status === 'RESOLVED' && (
                          <button
                            onClick={() => onStatusChange(r.id, 'ACTIVE')}
                            className="px-3 py-1 text-xs font-semibold rounded-lg bg-slate-800 text-slate-400 hover:text-white transition"
                          >
                            Reopen
                          </button>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          );
        }
        """);

        // 5. components/CreateItemForm.tsx
        files.put("src/components/CreateItemForm.tsx", """
        import React, { useState } from 'react';
        import { DomainRecord, DomainPriority } from '../types';

        interface CreateItemFormProps {
          onSubmit: (record: DomainRecord) => void;
          onCancel: () => void;
        }

        export default function CreateItemForm({ onSubmit, onCancel }: CreateItemFormProps) {
          const [title, setTitle] = useState('');
          const [category, setCategory] = useState('Operations');
          const [priority, setPriority] = useState<DomainPriority>('NORMAL');
          const [description, setDescription] = useState('');
          const [assignee, setAssignee] = useState('Operations Lead');
          const [error, setError] = useState<string | null>(null);

          const handleSubmit = (e: React.FormEvent) => {
            e.preventDefault();
            if (!title.trim()) {
              setError('Please enter a descriptive title for this record.');
              return;
            }
            if (!description.trim()) {
              setError('Please provide operational details or description.');
              return;
            }

            const newRec: DomainRecord = {
              id: 'REC-' + Math.floor(100 + Math.random() * 900),
              title: title.trim(),
              category,
              priority,
              status: 'ACTIVE',
              description: description.trim(),
              assignee,
              updatedAt: 'Just now'
            };

            onSubmit(newRec);
          };

          return (
            <div className="max-w-2xl mx-auto bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-2xl animate-fade-in">
              <div className="flex items-center justify-between pb-4 border-b border-slate-800 mb-5">
                <div>
                  <h3 className="text-lg font-bold text-white flex items-center gap-2">
                    <span>➕</span> Create New Record
                  </h3>
                  <p className="text-xs text-slate-400 mt-0.5">Submit verified item to workspace queue</p>
                </div>
                <button onClick={onCancel} className="text-slate-400 hover:text-white text-xs">
                  ✕ Cancel
                </button>
              </div>

              {error && (
                <div className="mb-4 p-3 rounded-xl bg-rose-950/60 border border-rose-500/50 text-rose-300 text-xs font-semibold">
                  ⚠️ {error}
                </div>
              )}

              <form onSubmit={handleSubmit} className="space-y-4">
                <div>
                  <label className="block text-xs font-bold text-slate-300 mb-1.5">Record Title *</label>
                  <input
                    type="text"
                    placeholder="e.g. Deploy Customer Integration Pipeline"
                    value={title}
                    onChange={e => { setTitle(e.target.value); setError(null); }}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2 text-xs text-white focus:outline-none focus:border-indigo-500"
                  />
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-bold text-slate-300 mb-1.5">Category</label>
                    <select
                      value={category}
                      onChange={e => setCategory(e.target.value)}
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2 text-xs text-white focus:outline-none focus:border-indigo-500"
                    >
                      <option value="Operations">Operations</option>
                      <option value="Onboarding">Onboarding</option>
                      <option value="Compliance">Compliance</option>
                      <option value="Delivery">Delivery</option>
                      <option value="Support">Support</option>
                    </select>
                  </div>

                  <div>
                    <label className="block text-xs font-bold text-slate-300 mb-1.5">Priority Level</label>
                    <select
                      value={priority}
                      onChange={e => setPriority(e.target.value as DomainPriority)}
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2 text-xs text-white focus:outline-none focus:border-indigo-500"
                    >
                      <option value="URGENT">URGENT</option>
                      <option value="HIGH">HIGH</option>
                      <option value="NORMAL">NORMAL</option>
                      <option value="LOW">LOW</option>
                    </select>
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-300 mb-1.5">Assignee</label>
                  <input
                    type="text"
                    value={assignee}
                    onChange={e => setAssignee(e.target.value)}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2 text-xs text-white focus:outline-none focus:border-indigo-500"
                  />
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-300 mb-1.5">Operational Details *</label>
                  <textarea
                    rows={4}
                    placeholder="Describe specific inputs, constraints, or verification criteria..."
                    value={description}
                    onChange={e => { setDescription(e.target.value); setError(null); }}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2 text-xs text-white focus:outline-none focus:border-indigo-500"
                  />
                </div>

                <div className="flex items-center justify-end gap-3 pt-3 border-t border-slate-800">
                  <button
                    type="button"
                    onClick={onCancel}
                    className="px-4 py-2 rounded-xl text-xs font-semibold text-slate-400 hover:text-white transition"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="px-5 py-2 rounded-xl text-xs font-bold bg-indigo-600 hover:bg-indigo-500 text-white shadow-lg transition"
                  >
                    Create & Dispatch Record
                  </button>
                </div>
              </form>
            </div>
          );
        }
        """);

        // 6. components/AnalyticsView.tsx
        files.put("src/components/AnalyticsView.tsx", """
        import { DomainRecord } from '../types';

        export default function AnalyticsView({ records }: { records: DomainRecord[] }) {
          const total = records.length;
          const active = records.filter(r => r.status === 'ACTIVE').length;
          const resolved = records.filter(r => r.status === 'RESOLVED').length;
          const pending = records.filter(r => r.status === 'PENDING').length;

          const activePct = total > 0 ? Math.round((active / total) * 100) : 0;
          const resolvedPct = total > 0 ? Math.round((resolved / total) * 100) : 0;
          const pendingPct = total > 0 ? Math.round((pending / total) * 100) : 0;

          return (
            <div className="space-y-6 animate-fade-in max-w-4xl mx-auto">
              <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl">
                <h3 className="text-base font-bold text-white mb-2 flex items-center gap-2">
                  <span>📊</span> Operational Status Distribution
                </h3>
                <p className="text-xs text-slate-400 mb-6">Real-time status breakdown across all workspace records.</p>

                {/* Progress Multi-Bar */}
                <div className="h-4 bg-slate-950 rounded-full overflow-hidden flex mb-4">
                  <div style={{ width: `${resolvedPct}%%` }} className="bg-emerald-500 transition-all" title={`Resolved: ${resolvedPct}%%`} />
                  <div style={{ width: `${activePct}%%` }} className="bg-indigo-500 transition-all" title={`Active: ${activePct}%%`} />
                  <div style={{ width: `${pendingPct}%%` }} className="bg-amber-500 transition-all" title={`Pending: ${pendingPct}%%`} />
                </div>

                <div className="grid grid-cols-3 gap-4 text-center">
                  <div className="p-3 bg-slate-950/60 rounded-xl border border-slate-800">
                    <span className="text-xs font-bold text-emerald-400">RESOLVED</span>
                    <p className="text-2xl font-black text-white mt-1">{resolved}</p>
                    <span className="text-[10px] text-slate-400">{resolvedPct}%% of total</span>
                  </div>
                  <div className="p-3 bg-slate-950/60 rounded-xl border border-slate-800">
                    <span className="text-xs font-bold text-indigo-400">ACTIVE</span>
                    <p className="text-2xl font-black text-white mt-1">{active}</p>
                    <span className="text-[10px] text-slate-400">{activePct}%% of total</span>
                  </div>
                  <div className="p-3 bg-slate-950/60 rounded-xl border border-slate-800">
                    <span className="text-xs font-bold text-amber-400">PENDING</span>
                    <p className="text-2xl font-black text-white mt-1">{pending}</p>
                    <span className="text-[10px] text-slate-400">{pendingPct}%% of total</span>
                  </div>
                </div>
              </div>

              <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl">
                <h4 className="text-sm font-bold text-white mb-3">Operational Compliance & Telemetry</h4>
                <div className="space-y-3 text-xs">
                  <div className="flex justify-between items-center py-2 border-b border-slate-800">
                    <span className="text-slate-300">Isolated Workspace Health</span>
                    <span className="font-bold text-emerald-400 font-mono">100%% OK</span>
                  </div>
                  <div className="flex justify-between items-center py-2 border-b border-slate-800">
                    <span className="text-slate-300">Local State Persistence</span>
                    <span className="font-bold text-cyan-400 font-mono">ENABLED</span>
                  </div>
                  <div className="flex justify-between items-center py-2">
                    <span className="text-slate-300">Runtime Pipeline Speed</span>
                    <span className="font-bold text-indigo-300 font-mono">&lt; 15ms</span>
                  </div>
                </div>
              </div>
            </div>
          );
        }
        """);

        // 7. App.tsx
        files.put("src/App.tsx", """
        import { useState, useEffect } from 'react';
        import { DomainRecord } from './types';
        import { initialDomainRecords, initialMetrics, initialActivities } from './mockData';
        import DashboardOverview from './components/DashboardOverview';
        import ItemManager from './components/ItemManager';
        import CreateItemForm from './components/CreateItemForm';
        import AnalyticsView from './components/AnalyticsView';

        type Screen = 'OVERVIEW' | 'RECORDS' | 'CREATE' | 'ANALYTICS';

        export default function App() {
          const [currentScreen, setCurrentScreen] = useState<Screen>('OVERVIEW');
          const [records, setRecords] = useState<DomainRecord[]>(() => {
            const saved = localStorage.getItem('%s_records');
            return saved ? JSON.parse(saved) : initialDomainRecords;
          });
          const [notification, setNotification] = useState<string | null>(null);

          useEffect(() => {
            localStorage.setItem('%s_records', JSON.stringify(records));
          }, [records]);

          const showToast = (msg: string) => {
            setNotification(msg);
            setTimeout(() => setNotification(null), 3500);
          };

          const handleStatusChange = (id: string, newStatus: DomainRecord['status']) => {
            setRecords(prev => prev.map(r => r.id === id ? { ...r, status: newStatus, updatedAt: 'Just now' } : r));
            showToast(`Updated record ${id} status to ${newStatus}`);
          };

          const handleCreateRecord = (newRec: DomainRecord) => {
            setRecords(prev => [newRec, ...prev]);
            setCurrentScreen('RECORDS');
            showToast(`Created and dispatched record ${newRec.id}`);
          };

          return (
            <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans selection:bg-indigo-500 selection:text-white">
              {/* Notification Banner */}
              {notification && (
                <div className="fixed top-4 right-4 z-50 bg-emerald-950 text-emerald-200 border border-emerald-500/50 px-4 py-2.5 rounded-xl shadow-2xl text-xs font-semibold animate-fade-in flex items-center gap-2">
                  <span>✔</span>
                  <span>{notification}</span>
                </div>
              )}

              {/* Top Navigation Bar */}
              <header className="border-b border-slate-800 bg-slate-900/80 backdrop-blur-md px-6 py-3.5 sticky top-0 z-30 flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-indigo-600 to-cyan-400 flex items-center justify-center text-lg font-bold text-white shadow-md">
                    🚀
                  </div>
                  <div>
                    <h1 className="font-extrabold text-white text-base tracking-tight leading-none">%s</h1>
                    <span className="text-[11px] text-indigo-400 font-mono font-medium">%s</span>
                  </div>
                </div>

                {/* Desktop Tabs */}
                <nav className="flex items-center gap-1 bg-slate-950 p-1 rounded-xl border border-slate-800 text-xs font-semibold">
                  <button
                    onClick={() => setCurrentScreen('OVERVIEW')}
                    className={`px-3 py-1.5 rounded-lg transition ${currentScreen === 'OVERVIEW' ? 'bg-indigo-600 text-white shadow' : 'text-slate-400 hover:text-white'}`}
                  >
                    📊 Overview
                  </button>
                  <button
                    onClick={() => setCurrentScreen('RECORDS')}
                    className={`px-3 py-1.5 rounded-lg transition ${currentScreen === 'RECORDS' ? 'bg-indigo-600 text-white shadow' : 'text-slate-400 hover:text-white'}`}
                  >
                    📋 Records
                  </button>
                  <button
                    onClick={() => setCurrentScreen('CREATE')}
                    className={`px-3 py-1.5 rounded-lg transition ${currentScreen === 'CREATE' ? 'bg-indigo-600 text-white shadow' : 'text-slate-400 hover:text-white'}`}
                  >
                    ➕ New Record
                  </button>
                  <button
                    onClick={() => setCurrentScreen('ANALYTICS')}
                    className={`px-3 py-1.5 rounded-lg transition ${currentScreen === 'ANALYTICS' ? 'bg-indigo-600 text-white shadow' : 'text-slate-400 hover:text-white'}`}
                  >
                    📈 Analytics
                  </button>
                </nav>
              </header>

              {/* Sub-header Hero Bar */}
              <div className="bg-slate-900/40 border-b border-slate-800/80 px-6 py-2.5 flex items-center justify-between text-xs text-slate-400">
                <p className="truncate max-w-xl">
                  <span className="text-white font-medium">Tagline:</span> %s
                </p>
                <span className="text-[10px] font-mono bg-indigo-950/60 border border-indigo-500/30 text-indigo-300 px-2.5 py-0.5 rounded-full shrink-0">
                  ISOLATED WORKSPACE
                </span>
              </div>

              {/* Main Content Area */}
              <main className="flex-1 max-w-6xl w-full mx-auto p-6">
                {currentScreen === 'OVERVIEW' && (
                  <DashboardOverview
                    records={records}
                    metrics={initialMetrics}
                    activities={initialActivities}
                    onNavigate={setCurrentScreen}
                  />
                )}
                {currentScreen === 'RECORDS' && (
                  <ItemManager
                    records={records}
                    onStatusChange={handleStatusChange}
                    onOpenCreate={() => setCurrentScreen('CREATE')}
                  />
                )}
                {currentScreen === 'CREATE' && (
                  <CreateItemForm
                    onSubmit={handleCreateRecord}
                    onCancel={() => setCurrentScreen('RECORDS')}
                  />
                )}
                {currentScreen === 'ANALYTICS' && (
                  <AnalyticsView records={records} />
                )}
              </main>

              {/* Footer */}
              <footer className="border-t border-slate-800 bg-slate-900/50 py-4 px-6 text-center text-xs text-slate-400">
                <p>Generated via Autonomous Virtual Startup Simulator · Description-driven architecture</p>
              </footer>
            </div>
          );
        }
        """.formatted(appName, appName, appName, category, tagline));
    }

    private String generateReadme(Startup startup, ProductSpec spec) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(spec.appName()).append(" — Product MVP Web Application\n\n");
        sb.append("> **Generated via Autonomous Virtual Startup Simulator**\n\n");
        sb.append("## 1. Product Overview\n");
        sb.append("- **Category:** ").append(spec.category()).append("\n");
        sb.append("- **Tagline:** ").append(spec.tagline()).append("\n");
        sb.append("- **Summary:** ").append(spec.summary()).append("\n\n");

        sb.append("## 2. Target Users\n");
        for (String user : spec.targetUsers()) {
            sb.append("- ").append(user).append("\n");
        }
        sb.append("\n");

        sb.append("## 3. Primary Workflows\n");
        for (String wf : spec.primaryWorkflows()) {
            sb.append("- ").append(wf).append("\n");
        }
        sb.append("\n");

        sb.append("## 4. Implemented Screens & Features\n");
        for (var screen : spec.screens()) {
            sb.append("### ").append(screen.icon()).append(" ").append(screen.title()).append("\n");
            sb.append(screen.description()).append("\n\n");
            for (String feat : screen.keyFeatures()) {
                sb.append("  - ").append(feat).append("\n");
            }
            sb.append("\n");
        }

        sb.append("## 5. Prototype Limitations & Simulated Features\n");
        sb.append(spec.simulatedNotice()).append("\n\n");

        sb.append("## 6. How to Run Locally\n");
        sb.append("```bash\n");
        sb.append("npm install\n");
        sb.append("npm run dev\n");
        sb.append("```\n\n");
        sb.append("## 7. Production Build\n");
        sb.append("```bash\n");
        sb.append("npm run build\n");
        sb.append("npm run preview\n");
        sb.append("```\n");

        return sb.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String escapeJs(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
    }
}
