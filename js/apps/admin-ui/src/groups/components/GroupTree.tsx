import { useState } from "react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import {
  Checkbox,
  Dropdown,
  DropdownItem,
  DropdownPosition,
  DropdownSeparator,
  InputGroup,
  KebabToggle,
  Tooltip,
  TreeView,
  TreeViewDataItem,
} from "@patternfly/react-core";

import type GroupRepresentation from "@keycloak/keycloak-admin-client/lib/defs/groupRepresentation";
import { useAdminClient, useFetch } from "../../context/auth/AdminClient";
import { KeycloakSpinner } from "../../components/keycloak-spinner/KeycloakSpinner";
import useToggle from "../../utils/useToggle";
import { DeleteGroup } from "./DeleteGroup";
import { GroupsModal } from "../GroupsModal";
import { MoveDialog } from "./MoveDialog";
import { PaginatingTableToolbar } from "../../components/table-toolbar/PaginatingTableToolbar";
import { useSubGroups } from "../SubGroupsContext";
import { fetchAdminUI } from "../../context/auth/admin-ui-endpoint";
import { useRealm } from "../../context/realm-context/RealmContext";
import { joinPath } from "../../utils/joinPath";

import "./group-tree.css";

type GroupTreeContextMenuProps = {
  group: GroupRepresentation;
  refresh: () => void;
};

const GroupTreeContextMenu = ({
  group,
  refresh,
}: GroupTreeContextMenuProps) => {
  const { t } = useTranslation("groups");

  const [isOpen, toggleOpen] = useToggle();
  const [renameOpen, toggleRenameOpen] = useToggle();
  const [createOpen, toggleCreateOpen] = useToggle();
  const [moveOpen, toggleMoveOpen] = useToggle();
  const [deleteOpen, toggleDeleteOpen] = useToggle();

  return (
    <>
      {renameOpen && (
        <GroupsModal
          id={group.id}
          rename={group.name}
          refresh={() => {
            refresh();
          }}
          handleModalToggle={toggleRenameOpen}
        />
      )}
      {createOpen && (
        <GroupsModal
          id={group.id}
          handleModalToggle={toggleCreateOpen}
          refresh={refresh}
        />
      )}
      {moveOpen && (
        <MoveDialog source={group} refresh={refresh} onClose={toggleMoveOpen} />
      )}
      <DeleteGroup
        show={deleteOpen}
        toggleDialog={toggleDeleteOpen}
        selectedRows={[group]}
        refresh={refresh}
      />
      <Dropdown
        toggle={<KebabToggle onToggle={toggleOpen} />}
        isOpen={isOpen}
        isPlain
        position={DropdownPosition.right}
        dropdownItems={[
          <DropdownItem key="rename" onClick={toggleRenameOpen}>
            {t("rename")}
          </DropdownItem>,
          <DropdownItem key="move" onClick={toggleMoveOpen}>
            {t("moveTo")}
          </DropdownItem>,
          <DropdownItem key="create" onClick={toggleCreateOpen}>
            {t("createChildGroup")}
          </DropdownItem>,
          <DropdownSeparator key="separator" />,
          <DropdownItem key="delete" onClick={toggleDeleteOpen}>
            {t("common:delete")}
          </DropdownItem>,
        ]}
      />
    </>
  );
};

type GroupTreeProps = {
  refresh: () => void;
  canViewDetails: boolean;
};

export const GroupTree = ({
  refresh: viewRefresh,
  canViewDetails,
}: GroupTreeProps) => {
  const { t } = useTranslation("groups");
  const { adminClient } = useAdminClient();
  const { realm } = useRealm();

  const [data, setData] = useState<TreeViewDataItem[]>();
  const { subGroups, setSubGroups } = useSubGroups();

  const [search, setSearch] = useState("");
  const [max, setMax] = useState(20);
  const [first, setFirst] = useState(0);
  const [exact, setExact] = useState(false);

  const [key, setKey] = useState(0);
  const refresh = () => {
    setKey(key + 1);
    viewRefresh();
  };

  const mapGroup = (
    group: GroupRepresentation,
    parents: GroupRepresentation[],
    refresh: () => void
  ): TreeViewDataItem => {
    const groups = [...parents, group];
    return {
      id: group.id,
      name: (
        <Tooltip content={group.name}>
          {canViewDetails ? (
            <Link
              to={`/${realm}/groups/${joinPath(...groups.map((g) => g.id!))}`}
              onClick={() => setSubGroups(groups)}
            >
              {group.name}
            </Link>
          ) : (
            <span>{group.name}</span>
          )}
        </Tooltip>
      ),
      children:
        group.subGroups && group.subGroups.length > 0
          ? group.subGroups.map((g) => mapGroup(g, groups, refresh))
          : undefined,
      action: canViewDetails && (
        <GroupTreeContextMenu group={group} refresh={refresh} />
      ),
      defaultExpanded: subGroups.map((g) => g.id).includes(group.id),
    };
  };

  useFetch(
    () =>
      fetchAdminUI<GroupRepresentation[]>(
        adminClient,
        "ui-ext/groups",
        Object.assign(
          {
            first: `${first}`,
            max: `${max + 1}`,
            exact: `${exact}`,
          },
          search === "" ? null : { search }
        )
      ),
    (groups) => setData(groups.map((g) => mapGroup(g, [], refresh))),
    [key, first, max, search, exact]
  );

  return data ? (
    <PaginatingTableToolbar
      count={data.length || 0}
      first={first}
      max={max}
      onNextClick={setFirst}
      onPreviousClick={setFirst}
      onPerPageSelect={(first, max) => {
        setFirst(first);
        setMax(max);
      }}
      inputGroupName="searchForGroups"
      inputGroupPlaceholder={t("groups:searchForGroups")}
      inputGroupOnEnter={setSearch}
      toolbarItem={
        <InputGroup className="pf-u-pt-sm">
          <Checkbox
            id="exact"
            data-testid="exact-search"
            name="exact"
            isChecked={exact}
            onChange={(value) => setExact(value)}
          />
          <label htmlFor="exact" className="pf-u-pl-sm">
            {t("exactSearch")}
          </label>
        </InputGroup>
      }
    >
      {data.length > 0 && (
        <TreeView
          data={data}
          allExpanded={search.length > 0}
          hasGuides
          className="keycloak_groups_treeview"
        />
      )}
    </PaginatingTableToolbar>
  ) : (
    <KeycloakSpinner />
  );
};
