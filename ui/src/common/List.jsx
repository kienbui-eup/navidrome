import React from 'react'
import {
  ListBase,
  ListView,
  ListToolbar,
  ListActions,
  useListContext,
} from 'react-admin'
import { makeStyles } from '@material-ui/core/styles'
import config from '../config'
import { Pagination } from './Pagination'
import { Title } from './index'
import { PageHeaderContent } from '../layout/PageHeader'
import { ContinuousListBase, ContinuousListEnd } from './ContinuousList'

const useStyles = makeStyles((theme) => ({
  bulkActionsDisplayed: { '&&': { marginTop: 0 } },
  continuous: { minHeight: '100%', display: 'flex', flexDirection: 'column' },
  continuousMain: { flex: 1 },
  toolbar: {
    '&&': {
      minHeight: 44,
      padding: '0 !important',
      background: 'transparent',
      alignItems: 'center',
      justifyContent: 'center',
      flexWrap: 'wrap',
      gap: theme.spacing(1.5),
    },
    '& form': {
      flex: '1 1 160px',
      maxWidth: 520,
      minWidth: 0,
      minHeight: 0,
      marginTop: 0,
      alignItems: 'center',
    },
    '& .filter-field': { minWidth: 0 },
    '& .filter-field:first-child': { flex: '1 1 240px' },
    '& .filter-field:first-child > .MuiFormControl-root': { width: '100%' },
    '& .filter-field > .MuiFormControl-root': {
      marginTop: 0,
      marginBottom: 0,
      maxWidth: '100%',
    },
    '& .filter-field .MuiInputBase-root': { minHeight: 44 },
    '& .filter-field > div:last-child': { width: theme.spacing(1) },
    '& > span:empty': { display: 'none' },
    [theme.breakpoints.down('xs')]: {
      '&&': { display: 'contents' },
      '& form': { gridColumn: '1 / 3', gridRow: 2, maxWidth: 'none' },
      '& .filter-field:first-child': { flex: '1 1 180px' },
      '& .filter-field:first-child > .MuiFormControl-root': { width: '100%' },
    },
  },
  actions: {
    '&&': {
      minHeight: 44,
      padding: 0,
      background: 'transparent',
      alignItems: 'center',
      flex: '0 0 auto',
      gap: theme.spacing(0.5),
    },
    '& button': { minWidth: 44, minHeight: 44 },
    [theme.breakpoints.down('xs')]: { gridColumn: 3, gridRow: 2 },
  },
}))

const HeaderListView = ({
  filters,
  actions = <ListActions />,
  infinite,
  ...props
}) => {
  const { loadMore, hasMore, loadingMore, queryKey, ...controller } =
    useListContext()
  const classes = useStyles()
  return (
    <>
      <PageHeaderContent replaceTitle={!!filters}>
        <ListToolbar
          {...controller}
          filters={filters}
          actions={actions}
          classes={{ toolbar: classes.toolbar, actions: classes.actions }}
        />
      </PageHeaderContent>
      <ListView
        {...props}
        {...controller}
        classes={{
          ...props.classes,
          bulkActionsDisplayed: classes.bulkActionsDisplayed,
          ...(infinite && {
            root: classes.continuous,
            main: classes.continuousMain,
          }),
        }}
        actions={false}
        filters={undefined}
        pagination={infinite ? <ContinuousListEnd /> : props.pagination}
      />
    </>
  )
}

export const List = ({ infinite = false, ...props }) => {
  const { resource } = props
  const listProps = {
    title: (
      <Title
        subTitle={`resources.${resource}.name`}
        args={{ smart_count: 2 }}
      />
    ),
    debounce: config.uiSearchDebounceMs,
    filter: {},
    perPage: 15,
    pagination: <Pagination />,
    ...props,
  }
  const Base = infinite ? ContinuousListBase : ListBase
  return (
    <Base {...listProps}>
      <HeaderListView {...listProps} infinite={infinite} />
    </Base>
  )
}
