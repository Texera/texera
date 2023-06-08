import { Component, EventEmitter, Input, Output } from "@angular/core";
import { SortMethod } from "../../type/sort-method";
import { DashboardEntry } from "../../type/dashboard-entry";

@Component({
  selector: "texera-sort-button",
  templateUrl: "./sort-button.component.html",
  styleUrls: ["./sort-button.component.scss"],
})
export class SortButtonComponent {
  public sortMethod = SortMethod.EditTimeDesc;
  _entries?: ReadonlyArray<DashboardEntry>;

  @Input() get entries(): ReadonlyArray<DashboardEntry> {
    if (!this._entries) {
      throw new Error("entries property must be set for SortButtonComponent.");
    }
    return this._entries;
  }
  set entries(value: ReadonlyArray<DashboardEntry>) {
    const update = () => {
      this._entries = value;
      this.sort();
      this.entriesChange.emit(this._entries);
    };
    // Update entries property only if the input differ from existing value. This breaks the infinite recursion.
    if (this._entries === undefined || value.length != this._entries.length) {
      update();
      return;
    }
    for (let i = 0; i < value.length; i++) {
      if (value[i] != this.entries[i]) {
        update();
        return;
      }
    }
  }

  @Output() entriesChange = new EventEmitter<typeof this.entries>();

  /**
   * Sort the workflows according to the sortMethod variable
   */
  public sort(): void {
    switch (this.sortMethod) {
      case SortMethod.NameAsc:
        this.ascSort();
        break;
      case SortMethod.NameDesc:
        this.dscSort();
        break;
      case SortMethod.EditTimeDesc:
        this.lastSort();
        break;
      case SortMethod.CreateTimeDesc:
        this.dateSort();
        break;
    }
  }

  /**
   * sort the workflow by name in ascending order
   */
  public ascSort(): void {
    this.sortMethod = SortMethod.NameAsc;
    this.entries = this.entries.slice().sort((t1, t2) => t1.name.toLowerCase().localeCompare(t2.name.toLowerCase()));
  }

  /**
   * sort the project by name in descending order
   */
  public dscSort(): void {
    this.sortMethod = SortMethod.NameDesc;
    this.entries = this.entries.slice().sort((t1, t2) => t2.name.toLowerCase().localeCompare(t1.name.toLowerCase()));
  }

  /**
   * sort the project by creating time in descending order
   */
  public dateSort(): void {
    this.sortMethod = SortMethod.CreateTimeDesc;
    this.entries = this.entries
      .slice()
      .sort((t1, t2) =>
        t1.creationTime !== undefined && t2.creationTime !== undefined ? t2.creationTime - t1.creationTime : 0
      );
  }

  /**
   * sort the project by last modified time in descending order
   */
  public lastSort(): void {
    this.sortMethod = SortMethod.EditTimeDesc;
    this.entries = this.entries
      .slice()
      .sort((t1, t2) =>
        t1.lastModifiedTime !== undefined && t2.lastModifiedTime !== undefined
          ? t2.lastModifiedTime - t1.lastModifiedTime
          : 0
      );
  }
}
