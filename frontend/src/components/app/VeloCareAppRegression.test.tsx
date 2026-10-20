// @vitest-environment happy-dom
// @ts-expect-error React 18 testing flag
globalThis.IS_REACT_ACT_ENVIRONMENT = true;
import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import App from "../../../../backend/workspaces/startup-3/src/App";
import ServiceBookingForm from "../../../../backend/workspaces/startup-3/src/components/ServiceBookingForm";

function changeInput(input: HTMLInputElement, value: string) {
  const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")?.set;
  if (nativeSetter) {
    nativeSetter.call(input, value);
  } else {
    input.value = value;
  }
  input.dispatchEvent(new Event("input", { bubbles: true }));
  input.dispatchEvent(new Event("change", { bubbles: true }));
}

describe("VeloCare Ops Generated App Regressions", () => {
  let container: HTMLDivElement;
  let root: Root;

  beforeEach(() => {
    localStorage.clear();
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
    vi.clearAllMocks();
  });

  afterEach(async () => {
    await act(async () => {
      root.unmount();
    });
    container.remove();
    localStorage.clear();
  });

  describe("Service Booking Form", () => {
    it("validates required fields and does not submit when name or model is missing", async () => {
      const onAddJob = vi.fn();
      const onCancel = vi.fn();

      await act(async () => {
        root.render(<ServiceBookingForm onAddJob={onAddJob} onCancel={onCancel} />);
      });

      // Submit with empty fields
      const submitBtn = Array.from(container.querySelectorAll("button")).find(b =>
        b.textContent?.includes("+ Register Ticket")
      )!;

      await act(async () => {
        submitBtn.click();
      });

      expect(onAddJob).not.toHaveBeenCalled();
      expect(container.textContent).toContain("Customer full name is required.");
      expect(container.textContent).toContain("Bicycle brand and model is required.");
    });

    it("creates a repair ticket immediately and synchronously on valid submission", async () => {
      const onAddJob = vi.fn();
      const onCancel = vi.fn();

      await act(async () => {
        root.render(<ServiceBookingForm onAddJob={onAddJob} onCancel={onCancel} />);
      });

      // Fill in customer name
      const nameInput = container.querySelector<HTMLInputElement>("#customer-name-input")!;
      await act(async () => {
        changeInput(nameInput, "Alex Rider");
      });

      // Fill in bike model
      const modelInput = container.querySelector<HTMLInputElement>("#bike-model-input")!;
      await act(async () => {
        changeInput(modelInput, "Trek Domane AL 3");
      });

      // Click URGENT priority
      const urgentBtn = Array.from(container.querySelectorAll<HTMLButtonElement>(".btn-priority")).find(b =>
        b.textContent === "URGENT"
      )!;
      await act(async () => {
        urgentBtn.click();
      });

      // Submit
      const submitBtn = Array.from(container.querySelectorAll<HTMLButtonElement>("button")).find(b =>
        b.textContent?.includes("+ Register Ticket")
      )!;

      await act(async () => {
        submitBtn.click();
      });

      expect(onAddJob).toHaveBeenCalledTimes(1);
      const createdJob = onAddJob.mock.calls[0][0];
      expect(createdJob.customerName).toBe("Alex Rider");
      expect(createdJob.bikeModel).toBe("Trek Domane AL 3");
      expect(createdJob.priority).toBe("URGENT");
      expect(createdJob.stage).toBe("RECEIVED");
      expect(createdJob.id).toMatch(/^JOB-\d+/);
    });

    it("cancelling form calls onCancel and does not create a ticket", async () => {
      const onAddJob = vi.fn();
      const onCancel = vi.fn();

      await act(async () => {
        root.render(<ServiceBookingForm onAddJob={onAddJob} onCancel={onCancel} />);
      });

      const cancelBtn = Array.from(container.querySelectorAll<HTMLButtonElement>("button")).find(b =>
        b.textContent === "Cancel"
      )!;

      await act(async () => {
        cancelBtn.click();
      });

      expect(onCancel).toHaveBeenCalledTimes(1);
      expect(onAddJob).not.toHaveBeenCalled();
    });
  });

  describe("App Full Integration & Screen Navigation", () => {
    it("loads on Overview dashboard by default and navigates cleanly across tabs without bullets", async () => {
      await act(async () => {
        root.render(<App />);
      });

      // Top Header Branding
      expect(container.textContent).toContain("VELOCARE OPS");
      expect(container.textContent).toContain("Shop Active • Stand Bays Online");

      // Overview dashboard KPIs
      expect(container.textContent).toContain("Active Repair Queue");
      expect(container.textContent).toContain("Today's Bookings");
      expect(container.textContent).toContain("Low Stock Alerts");

      // Verify no unstyled bullet lists (UL elements in sidebar should not have list-style disc)
      const navButtons = Array.from(container.querySelectorAll<HTMLButtonElement>(".nav-button"));
      expect(navButtons.length).toBe(5);

      // Switch to Repair Jobs
      const repairJobsBtn = navButtons.find(b => b.textContent?.includes("Repair Jobs"))!;
      await act(async () => {
        repairJobsBtn.click();
      });

      expect(container.textContent).toContain("Workshop Service Kanban Board");
      expect(container.textContent).toContain("Received Intake");
      expect(container.textContent).toContain("On Repair Stand");

      // Switch to Inventory
      const inventoryBtn = navButtons.find(b => b.textContent?.includes("Parts Inventory"))!;
      await act(async () => {
        inventoryBtn.click();
      });

      expect(container.textContent).toContain("Workshop Spare Parts Inventory");
      expect(container.textContent).toContain("SKU & Component");

      // Switch to Invoices
      const invoicesBtn = navButtons.find(b => b.textContent?.includes("Invoices & Billing"))!;
      await act(async () => {
        invoicesBtn.click();
      });

      expect(container.textContent).toContain("Workshop Invoices & Customer Billing");
      expect(container.textContent).toContain("All Customer Statements");
    });

    it("submitting booking form creates ticket, switches to Kanban queue, and updates counts", async () => {
      await act(async () => {
        root.render(<App />);
      });

      // Navigate to Book Service
      const navButtons = Array.from(container.querySelectorAll<HTMLButtonElement>(".nav-button"));
      const bookBtn = navButtons.find(b => b.textContent?.includes("Book Service"))!;

      await act(async () => {
        bookBtn.click();
      });

      expect(container.textContent).toContain("Inbound Service Booking Desk");

      // Fill in form with changeInput
      const nameInput = container.querySelector<HTMLInputElement>("#customer-name-input")!;
      const modelInput = container.querySelector<HTMLInputElement>("#bike-model-input")!;

      await act(async () => {
        changeInput(nameInput, "Rachel Green");
        changeInput(modelInput, "Cervelo Aspero Gravel");
      });

      // Click register
      const submitBtn = Array.from(container.querySelectorAll<HTMLButtonElement>("button")).find(b =>
        b.textContent?.includes("+ Register Ticket")
      )!;

      await act(async () => {
        submitBtn.click();
      });

      // Must have switched to Kanban and rendered the new job!
      expect(container.textContent).toContain("Workshop Service Kanban Board");
      expect(container.textContent).toContain("Cervelo Aspero Gravel");
      expect(container.textContent).toContain("Rachel Green");
    });

    it("preserves created jobs in localStorage across simulated reloads", async () => {
      // First mount
      await act(async () => {
        root.render(<App />);
      });

      // Go to booking
      const bookBtn = Array.from(container.querySelectorAll<HTMLButtonElement>(".nav-button")).find(b =>
        b.textContent?.includes("Book Service")
      )!;
      await act(async () => {
        bookBtn.click();
      });

      // Fill in
      const nameInput = container.querySelector<HTMLInputElement>("#customer-name-input")!;
      const modelInput = container.querySelector<HTMLInputElement>("#bike-model-input")!;
      await act(async () => {
        changeInput(nameInput, "Persisted Rider");
        changeInput(modelInput, "Pinarello Dogma F");
      });

      // Submit
      const submitBtn = Array.from(container.querySelectorAll<HTMLButtonElement>("button")).find(b =>
        b.textContent?.includes("+ Register Ticket")
      )!;
      await act(async () => {
        submitBtn.click();
      });

      expect(container.textContent).toContain("Pinarello Dogma F");

      // Unmount
      await act(async () => {
        root.unmount();
      });

      // Remount a fresh instance
      root = createRoot(container);
      await act(async () => {
        root.render(<App />);
      });

      // Navigate to Kanban
      const kanbanBtn = Array.from(container.querySelectorAll<HTMLButtonElement>(".nav-button")).find(b =>
        b.textContent?.includes("Repair Jobs")
      )!;
      await act(async () => {
        kanbanBtn.click();
      });

      // Data must be restored from localStorage!
      expect(container.textContent).toContain("Pinarello Dogma F");
      expect(container.textContent).toContain("Persisted Rider");
    });
  });
});
