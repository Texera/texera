/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormlyFieldConfig } from "@ngx-formly/core";
import { EditableLabelWrapperComponent } from "./editable-label-wrapper.component";

describe("EditableLabelWrapperComponent", () => {
  let component: EditableLabelWrapperComponent;
  let fixture: ComponentFixture<EditableLabelWrapperComponent>;

  const state = (overrides: Partial<Parameters<typeof EditableLabelWrapperComponent.decorate>[1]> = {}) => ({
    authoring: true,
    name: "My input",
    hidden: false,
    fallback: "File Key",
    ...overrides,
  });

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EditableLabelWrapperComponent],
    }).compileComponents();
    fixture = TestBed.createComponent(EditableLabelWrapperComponent);
    component = fixture.componentInstance;
  });

  describe("decorate", () => {
    it("appends the wrapper after any existing ones", () => {
      const config: FormlyFieldConfig = { key: "k", wrappers: ["form-field"] };
      EditableLabelWrapperComponent.decorate(
        config,
        state(),
        () => {},
        () => {}
      );
      expect(config.wrappers).toEqual(["form-field", "editable-label-wrapper"]);
    });

    it("blanks formly's own label so the wrapper does not print it twice", () => {
      const config: FormlyFieldConfig = { key: "k", props: { label: "File Key" } };
      EditableLabelWrapperComponent.decorate(
        config,
        state(),
        () => {},
        () => {}
      );
      expect(config.props?.["label"]).toBe("");
    });

    it("maps the naming state and callbacks into props", () => {
      const rename = vi.fn();
      const setHidden = vi.fn();
      const config: FormlyFieldConfig = { key: "k" };
      EditableLabelWrapperComponent.decorate(config, state({ name: "Genes", hidden: true }), rename, setHidden);
      expect(config.props?.["authoring"]).toBe(true);
      expect(config.props?.["authorName"]).toBe("Genes");
      expect(config.props?.["authorHidden"]).toBe(true);
      expect(config.props?.["schemaLabel"]).toBe("File Key");
      expect(config.props?.["renameField"]).toBe(rename);
      expect(config.props?.["setFieldHidden"]).toBe(setHidden);
    });

    it("defaults canHide to true, and honors an explicit false", () => {
      const shown: FormlyFieldConfig = { key: "k" };
      EditableLabelWrapperComponent.decorate(
        shown,
        state(),
        () => {},
        () => {}
      );
      expect(shown.props?.["canHide"]).toBe(true);

      const locked: FormlyFieldConfig = { key: "k" };
      EditableLabelWrapperComponent.decorate(
        locked,
        state({ canHide: false }),
        () => {},
        () => {}
      );
      expect(locked.props?.["canHide"]).toBe(false);
    });
  });

  describe("handlers", () => {
    it("onRename forwards the input's value to renameField", () => {
      const rename = vi.fn();
      component.field = { props: { renameField: rename } } as unknown as FormlyFieldConfig;
      component.onRename({ target: { value: "New name" } } as unknown as Event);
      expect(rename).toHaveBeenCalledWith("New name");
    });

    it("onRename is inert on a reader mount, where decorate omitted rename", () => {
      const config: FormlyFieldConfig = { key: "k", props: { label: "Predicates" } };
      EditableLabelWrapperComponent.decorate(config, {
        authoring: false,
        name: "Predicate",
        hidden: false,
        fallback: "Predicates",
        canHide: false,
      });
      component.field = config;

      expect(config.props?.["label"]).toBe("");
      expect(() => component.onRename({ target: { value: "x" } } as unknown as Event)).not.toThrow();
    });

    it("onToggleHidden flips the current hidden state through setFieldHidden", () => {
      const setHidden = vi.fn();
      component.field = { props: { authorHidden: false, setFieldHidden: setHidden } } as unknown as FormlyFieldConfig;
      component.onToggleHidden();
      expect(setHidden).toHaveBeenCalledWith(true);
    });
  });

  describe("template", () => {
    it("while authoring, renders the name input seeded with authorName and the schema label as placeholder", () => {
      component.field = {
        props: { authoring: true, authorName: "Genes", schemaLabel: "File Key", canHide: true, authorHidden: false },
      } as unknown as FormlyFieldConfig;
      fixture.detectChanges();

      const input = fixture.nativeElement.querySelector("input.lbl-input") as HTMLInputElement;
      expect(input).toBeTruthy();
      expect(input.value).toBe("Genes");
      expect(input.placeholder).toBe("File Key");
      // the hide toggle is offered when canHide is not false
      expect(fixture.nativeElement.querySelector("button.lbl-eye")).toBeTruthy();
    });

    it("while authoring, still names the control for assistive technology, following the current name", () => {
      // The visible box edits the label; it is not the control's label. decorate blanks formly's
      // own, so without this the control below would have no accessible name while authoring.
      component.field = {
        id: "formly_3_genes",
        props: { authoring: true, authorName: "Genes", schemaLabel: "File Key", canHide: true },
      } as unknown as FormlyFieldConfig;
      fixture.detectChanges();

      const label = fixture.nativeElement.querySelector("label.lbl-sr-only") as HTMLLabelElement;
      expect(label.getAttribute("for")).toBe("formly_3_genes");
      expect(label.textContent?.trim()).toBe("Genes");
      // The name box itself is labelled on its own and never points at the control.
      expect(fixture.nativeElement.querySelector("input.lbl-input").getAttribute("aria-label")).toBeTruthy();

      component.field.props!["authorName"] = "";
      fixture.detectChanges();
      expect(label.textContent?.trim()).toBe("File Key");
    });

    it("hides the eye toggle when canHide is false", () => {
      component.field = {
        props: { authoring: true, authorName: "Genes", schemaLabel: "File Key", canHide: false },
      } as unknown as FormlyFieldConfig;
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector("button.lbl-eye")).toBeNull();
    });

    it("for a reader, renders a plain static label from authorName, tied to the control by id", () => {
      // decorate blanks formly's own label, so this one is the control's only label: without `for`
      // the reader's input would show a name but have no accessible name at all.
      component.field = {
        id: "formly_3_genes",
        props: { authoring: false, authorName: "Genes", schemaLabel: "File Key" },
      } as unknown as FormlyFieldConfig;
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector("input.lbl-input")).toBeNull();
      const label = fixture.nativeElement.querySelector("label.lbl-static") as HTMLLabelElement;
      expect(label.textContent?.trim()).toBe("Genes");
      expect(label.getAttribute("for")).toBe("formly_3_genes");
    });
  });
});
